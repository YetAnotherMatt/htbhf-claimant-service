package uk.gov.dhsc.htbhf.claimant.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.CollectionUtils;
import uk.gov.dhsc.htbhf.claimant.entity.Message;
import uk.gov.dhsc.htbhf.claimant.exception.EventFailedException;
import uk.gov.dhsc.htbhf.claimant.repository.MessageRepository;
import uk.gov.dhsc.htbhf.claimant.service.audit.EventAuditor;
import uk.gov.dhsc.htbhf.logging.event.FailureEvent;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static uk.gov.dhsc.htbhf.claimant.message.MessageStatus.ERROR;
import static uk.gov.dhsc.htbhf.logging.ExceptionDetailGenerator.constructExceptionDetail;

/**
 * Component that is triggered on a schedule and is responsible for finding all the messages that need to be
 * processed and passing them off to the {@link MessageTypeProcessor} matching the {@link MessageType} of
 * the message stored in the database.
 */
@RequiredArgsConstructor
@Slf4j
public class MessageProcessor {

    private final MessageStatusProcessor messageStatusProcessor;
    private final MessageRepository messageRepository;
    private final EventAuditor eventAuditor;
    private final Map<MessageType, MessageTypeProcessor> messageProcessorsByType;

    @Value("${message-processor.message-limit}")
    private final int messageProcessingLimit;

    public void processMessagesOfType(MessageType messageType) {
        List<Message> messages = messageRepository.findAllMessagesOfTypeWithTimestampBeforeNow(messageType, PageRequest.of(0, messageProcessingLimit));
        processMessagesOfType(messages, messageType);
    }

    private void processMessagesOfType(List<Message> messages, MessageType messageType) {
        if (CollectionUtils.isEmpty(messages)) {
            log.trace("No {} messages found to process", messageType);
            return;
        }

        log.info("Processing {} {} message(s)", messages.size(), messageType);
        MessageTypeProcessor messageTypeProcessor = getMessageTypeProcessor(messageType, messages);
        List<MessageStatus> statuses = messages.stream()
                .map(message -> processMessage(message, messageTypeProcessor))
                .collect(Collectors.toList());

        logResults(statuses, messageType, messageTypeProcessor);
    }

    private MessageTypeProcessor getMessageTypeProcessor(MessageType messageType, List<Message> messages) {
        MessageTypeProcessor messageTypeProcessor = messageProcessorsByType.get(messageType);
        if (messageTypeProcessor == null) {
            messageStatusProcessor.updateMessagesToErrorAndIncrementCount(messages);
            throw new IllegalArgumentException("No message type processor found in application context for message type: "
                    + messageType + ", there are " + messages.size() + " message(s) due to be processed");
        }
        return messageTypeProcessor;
    }

    private MessageStatus processMessage(Message message, MessageTypeProcessor messageTypeProcessor) {
        MessageStatus status = invokeMessageTypeProcessor(message, messageTypeProcessor);
        messageStatusProcessor.processStatusForMessage(message, status);
        return status;
    }

    private MessageStatus invokeMessageTypeProcessor(Message message, MessageTypeProcessor messageTypeProcessor) {
        try {
            return messageTypeProcessor.processMessage(message);
        } catch (EventFailedException efe) {
            log.error("Failure event caught for message with id {}, exception detail: {}", message.getId(), constructExceptionDetail(efe), efe);
            FailureEvent failureEvent = efe.getFailureEvent();
            eventAuditor.auditFailedEvent(failureEvent);
            messageTypeProcessor.processFailedMessage(message, failureEvent);
            return ERROR;
        } catch (RuntimeException e) {
            log.error("Unable to process message with id {}, exception detail: {}", message.getId(), constructExceptionDetail(e), e);
            return ERROR;
        }
    }

    private void logResults(List<MessageStatus> statuses, MessageType messageType, MessageTypeProcessor messageTypeProcessor) {
        Map<MessageStatus, Long> statusesCountMap = statuses.stream()
                .peek(messageStatus -> logNullMessageStatus(messageTypeProcessor, messageStatus))
                .filter(Objects::nonNull)
                .collect(groupingBy(identity(), counting()));

        statusesCountMap.forEach((messageStatus, count) -> log.info("Processed {} {} message(s) with status {}", count, messageType, messageStatus.name()));
    }

    private void logNullMessageStatus(MessageTypeProcessor messageTypeProcessor, MessageStatus messageStatus) {
        if (messageStatus == null) {
            log.error("Received null message status from MessageTypeProcessor: {}", messageTypeProcessor.getClass().getCanonicalName());
        }
    }

}
