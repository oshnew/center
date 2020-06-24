package my.study.center.common.websocket;

import lombok.extern.slf4j.Slf4j;
import my.study.center.common.app.documents.ChatMessageHist;
import my.study.center.common.app.repository.ChatMessageHistRepository;
import my.study.center.common.websocket.cd.ChatMessageType;
import my.study.center.common.websocket.dto.ChatMessage;
import my.study.center.common.websocket.dto.ChatUser;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.web.context.support.GenericWebApplicationContext;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static my.study.center.common.websocket.ReactiveWebsocketConnectionHandler.userSessionManager;

/**
 * 메세지를 전달 받아 처리하는 subscriber
 *
 * @author minssogi
 */
@Slf4j
public class ReactiveWebsocketSubscriber implements Subscriber<ChatMessage> {
    private UnicastProcessor<ChatMessage> eventPublisher;
    private Optional<ChatMessage> lastReceivedEvent;
    private ChatUser mySessionInfo; // 현재 세션 관리를 위한 객체
    private ChatMessageHistRepository chatMessageHistRepository;

    ReactiveWebsocketSubscriber(UnicastProcessor<ChatMessage> eventPublisher, ChatUser chatUser, ChatMessageHistRepository chatMessageHistRepository) {
        this.eventPublisher = eventPublisher;
        this.mySessionInfo = chatUser;
        lastReceivedEvent = Optional.empty();
        this.chatMessageHistRepository = chatMessageHistRepository;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        subscription.request(10_000);
    }

    /**
     * 실제로 클라가 메세지를 보내면 수신하는 부분
     *
     * @param chatMessage ChatMessage
     */
    @Override
    public void onNext(ChatMessage chatMessage) {
        mySessionInfo.getMessageCount().incrementAndGet(); // 현재 세션에서 보낸 메세지 총 갯수를 관리함.
        lastReceivedEvent = Optional.of(chatMessage);

        chatMessageHistRepository.save(new ChatMessageHist(chatMessage));
        eventPublisher.onNext(chatMessage);
    }

    @Override
    public void onError(Throwable throwable) {
        log.error("ERROR : {}", throwable.getMessage(), throwable);
    }

    @Override
    public void onComplete() {
        lastReceivedEvent.ifPresent(event -> eventPublisher.onNext(
                new ChatMessage(UUID.randomUUID().toString(), ChatMessageType.USER_LEFT, this.mySessionInfo.getUid() + " 님이 퇴장했습니다.",
                        Instant.now().toEpochMilli(), new ChatUser(this.mySessionInfo.getUid()))));
        userSessionManager.remove(this.mySessionInfo.getUid());
    }

    public ChatUser getMySessionInfo() {
        return this.mySessionInfo;
    }
}
