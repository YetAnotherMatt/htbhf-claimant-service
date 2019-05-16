package uk.gov.dhsc.htbhf.claimant.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.CollectionUtils;
import uk.gov.dhsc.htbhf.claimant.entity.Message;
import uk.gov.dhsc.htbhf.claimant.repository.MessageRepository;
import uk.gov.dhsc.htbhf.requestcontext.aop.NewRequestContextWithSessionId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

/**
 * Component that is triggered on a schedule and is responsible for finding all the messages that need to be
 * processed and passing them off to the {@link MessageTypeProcessor} matching the {@link MessageType} of
 * the message stored in the database.
 */
@RequiredArgsConstructor
@Slf4j
public class MessageProcessor {

    private final MessageRepository messageRepository;
    private final Map<MessageType, MessageTypeProcessor> messageProcessorsByType;

    @Scheduled(cron = "${message-processor.cron-schedule}")
    @SchedulerLock(
            name = "Process all messages",
            lockAtLeastForString = "${message-processor.minimum-lock-time}",
            lockAtMostForString = "${message-processor.maximum-lock-time}")
    @NewRequestContextWithSessionId(sessionId = "MessageProcessor")
    public void processAllMessages() {
        Stream.of(MessageType.values()).forEach(this::processMessagesOfType);
    }

    private void processMessagesOfType(MessageType messageType) {
        List<Message> messages = messageRepository.findAllMessagesByTypeOrderedByDate(messageType);
        if (CollectionUtils.isEmpty(messages)) {
            log.debug("No messages found to process for type: [{}]", messageType);
            return;
        }

        processMessages(messages, messageType);
    }

    private void processMessages(List<Message> messages, MessageType messageType) {
        log.info("Processing {} message(s) of type {}", messages.size(), messageType);
        MessageTypeProcessor messageTypeProcessor = getMessageTypeProcessor(messageType, messages);
        List<MessageStatus> statuses = messages.stream()
                .map(message -> processMessage(message, messageTypeProcessor))
                .collect(Collectors.toList());

        logResults(statuses, messageTypeProcessor);
    }

    private MessageTypeProcessor getMessageTypeProcessor(MessageType messageType, List<Message> messages) {
        MessageTypeProcessor messageTypeProcessor = messageProcessorsByType.get(messageType);
        if (messageTypeProcessor == null) {
            throw new IllegalArgumentException("No message type processor found in application context for message type: "
                    + messageType + ", there are " + messages.size() + " message(s) in the queue");
        }
        return messageTypeProcessor;
    }

    private MessageStatus processMessage(Message message, MessageTypeProcessor messageTypeProcessor) {
        try {
            MessageStatus status = messageTypeProcessor.processMessage(message);
            if (status == MessageStatus.COMPLETED) {
                messageRepository.delete(message);
            }
            // TODO: HTBHF-1285: handle other message statuses
            return status;
        } catch (RuntimeException e) {
            // TODO: HTBHF-1285: expose ErrorHandler.constructExceptionDetail
            //                  from java common and include it in error handling
            log.error("Unable to process message with id {}", message.getId(), e);
            // TODO: HTBHF-1285: increment delivery count and message timestamp
            return MessageStatus.ERROR;
        }
    }

    private void logResults(List<MessageStatus> statuses, MessageTypeProcessor messageTypeProcessor) {
        Map<MessageStatus, Long> statusesCountMap = statuses.stream()
                .peek(messageStatus -> logNullMessageStatus(messageTypeProcessor, messageStatus))
                .filter(Objects::nonNull)
                .collect(groupingBy(identity(), counting()));

        statusesCountMap.forEach((messageStatus, count) -> log.info("Processed {} message(s) with status {}", count, messageStatus.name()));
    }

    private void logNullMessageStatus(MessageTypeProcessor messageTypeProcessor, MessageStatus messageStatus) {
        if (messageStatus == null) {
            log.error("Received null message status from MessageTypeProcessor: {}", messageTypeProcessor.getClass().getCanonicalName());
        }
    }

}
