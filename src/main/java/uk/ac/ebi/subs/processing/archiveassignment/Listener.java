package uk.ac.ebi.subs.processing.archiveassignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.messaging.Topics;
import uk.ac.ebi.subs.repository.model.Submission;

@Component
public class Listener {

    private static final Logger logger = LoggerFactory.getLogger(Listener.class);

    private RabbitMessagingTemplate rabbitMessagingTemplate;
    private SubmissionArchiveAssignmentService submissionArchiveAssignmentService;

    @RabbitListener(queues = QueueConfig.SUBMISSION_ARCHIVE_ASSIGNMENT)
    public void assignArchives(Submission submission) {
        logger.info("assign archives {}", submission);

        submissionArchiveAssignmentService.assignArchives(submission);

        logger.info("archives assigned {}", submission);

        rabbitMessagingTemplate.convertAndSend(
                Exchanges.SUBMISSIONS,
                Topics.EVENT_SUBMISSION_PROCESSING_UPDATED,
                submission
        );
    }

    public Listener(RabbitMessagingTemplate rabbitMessagingTemplate, SubmissionArchiveAssignmentService submissionArchiveAssignmentService) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.submissionArchiveAssignmentService = submissionArchiveAssignmentService;
    }
}
