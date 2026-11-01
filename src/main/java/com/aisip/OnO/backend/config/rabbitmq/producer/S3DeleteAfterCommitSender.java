package com.aisip.OnO.backend.config.rabbitmq.producer;

import com.aisip.OnO.backend.config.rabbitmq.message.S3DeleteMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * DB 삭제가 커밋된 뒤에 S3 삭제 메시지를 보낸다.
 *
 * <p>트랜잭션 안에서 바로 보내면, 뒤에서 예외가 나 DB 삭제가 롤백돼도 S3 파일은 이미 지워진다.
 * 일괄 삭제에서 뒤쪽 id 가 실패해 앞서 지운 문제가 되살아나면 이미지가 없는 문제가 남았다.
 * 그래서 커밋된 경우에만 보내고, 롤백되면 아무것도 보내지 않는다.
 *
 * <p>트랜잭션 없이 호출되면 바로 보낸다. 전송이 실패해도 요청은 이미 커밋됐으므로 로그만 남긴다.
 * afterCommit 에서 새는 예외는 커밋을 마친 요청을 500 으로 만들기 때문에 여기서 모두 잡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3DeleteAfterCommitSender {

    private final S3DeleteProducer s3DeleteProducer;

    /**
     * @param messages 삭제할 이미지 URL 과 로그용 id. 한 트랜잭션에서 지운 것을 모아 한 번에 넘긴다
     */
    public void sendAfterCommit(List<S3DeleteMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<S3DeleteMessage> pending = List.copyOf(messages);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendAll(pending);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendAll(pending);
            }
        });
    }

    private void sendAll(List<S3DeleteMessage> messages) {
        for (S3DeleteMessage message : messages) {
            try {
                s3DeleteProducer.sendDeleteMessage(message.getImageUrl(), message.getProblemId());
            } catch (Exception e) {
                log.error("S3 삭제 메시지 전송 실패 - id: {}, error: {}",
                        message.getProblemId(), e.getMessage());
            }
        }
        log.info("S3 삭제 메시지 전송 완료 ({}개)", messages.size());
    }
}
