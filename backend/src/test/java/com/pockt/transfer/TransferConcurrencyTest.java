package com.pockt.transfer;

import com.pockt.infrastructure.exception.InsufficientBalanceException;
import com.pockt.transfer.dto.TransferRequest;
import com.pockt.transfer.service.TransferService;
import com.pockt.user.domain.User;
import com.pockt.user.repository.UserRepository;
import com.pockt.wallet.dto.WalletResponse;
import com.pockt.wallet.repository.WalletRepository;
import com.pockt.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "pockt.rate-limit.transfer-per-minute=100"
})
class TransferConcurrencyTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Test
    void concurrentTransfers_neverOverdraft() throws Exception {
        UUID senderUserId = UUID.randomUUID();
        String senderPhone = "+23480" + (1000000 + (int) (Math.random() * 8999999));
        User sender = new User(senderUserId, senderPhone, "Sender Concurrent", "hash", null, "VERIFIED", true, Instant.now(), Instant.now());
        userRepository.create(sender);
        WalletResponse senderWallet = walletService.createWallet(senderUserId, "USD");
        walletService.topUp(senderWallet.id(), 10000L, senderUserId); // $100.00

        UUID receiverUserId = UUID.randomUUID();
        String receiverPhone = "+23481" + (1000000 + (int) (Math.random() * 8999999));
        User receiver = new User(receiverUserId, receiverPhone, "Receiver Concurrent", "hash", null, "VERIFIED", true, Instant.now(), Instant.now());
        userRepository.create(receiver);
        walletService.createWallet(receiverUserId, "USD");

        int threadCount = 20;
        long eachTransferAmount = 1000L; // $10.00 each → exactly 10 can succeed

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        ConcurrentLinkedQueue<Boolean> results = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            final String idempotencyKey = "concurrency-key-" + UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startLatch.await();
                    transferService.send(
                            new TransferRequest(receiverPhone, eachTransferAmount, "USD", "Concurrent split", idempotencyKey),
                            senderUserId
                    );
                    results.add(true);
                } catch (InsufficientBalanceException e) {
                    results.add(false);
                } catch (Exception e) {
                    e.printStackTrace();
                    results.add(false);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all 20 threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        long succeeded = results.stream().filter(Boolean::booleanValue).count();
        long failed = results.stream().filter(r -> !r).count();

        long finalSenderBalance = walletRepository.getBalance(senderWallet.id());
        WalletResponse receiverWallet = walletService.getUserWallets(receiverUserId).getFirst();

        // Exactly 10 succeed, exactly 10 fail due to insufficient balance
        assertThat(succeeded).isEqualTo(10);
        assertThat(failed).isEqualTo(10);

        // Account is drained to 0, NEVER negative
        assertThat(finalSenderBalance).isEqualTo(0L);
        assertThat(receiverWallet.balance()).isEqualTo(10000L);
    }
}
