package uk.ac.ebi.subs.dispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.messaging.Queues;
import uk.ac.ebi.subs.messaging.Topics;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.repository.model.Submission;

import java.util.*;

@Service
public class DispatcherRabbitBridge {

    private static final Logger logger = LoggerFactory.getLogger(DispatcherRabbitBridge.class);

    RabbitMessagingTemplate rabbitMessagingTemplate;
    private DispatcherService dispatcherService;




    public DispatcherRabbitBridge(
            RabbitMessagingTemplate rabbitMessagingTemplate,
            MessageConverter messageConverter,
            DispatcherService dispatcherService

    ) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.rabbitMessagingTemplate.setMessageConverter(messageConverter);
        this.dispatcherService = dispatcherService;
    }

    /**
     * Submissions being submitted by a user causes a Submission message to be sent,
     * but downstream work needs a FullSubmission in a SubmissionEnvelope, so transform and resend
     *
     * @param submission
     */
    @RabbitListener(queues = Queues.SUBMISSION_SUBMITTED_DO_DISPATCH)
    public void onSubmissionDoDispatch(Submission submission) {
        logger.info("onSubmissionDoDispatch {}", submission);

        SubmissionEnvelope submissionEnvelope = dispatcherService.inflateInitialSubmission(submission);

        rabbitMessagingTemplate.convertAndSend(
                Exchanges.SUBMISSIONS,
                Topics.EVENT_SUBMISSION_UPDATED,
                submissionEnvelope
        );

    }

    @RabbitListener(queues = Queues.SUBMISSION_SUBMITTED_CHECK_SUPPORTING_INFO)
    public void onSubmissionCheckSupportingInfoRequirement(Submission submission) {
        logger.info("onSubmissionCheckSupportingInfoRequirement {}", submission);


        Map<Archive,SubmissionEnvelope> submissionEnvelopesForArchives = dispatcherService.requestSupportingInformation(submission);

        if (!submissionEnvelopesForArchives.containsKey(Archive.BioSamples)){
            return;
        }

        //TODO only handles BioSamples
        SubmissionEnvelope submissionEnvelope = submissionEnvelopesForArchives.get(Archive.BioSamples);

        if (submissionEnvelope.getSupportingSamplesRequired().isEmpty()){
            return;
        }

        rabbitMessagingTemplate.convertAndSend(
                Exchanges.SUBMISSIONS,
                Topics.EVENT_SUBMISSION_NEEDS_SAMPLES,
                submissionEnvelope
        );

    }





    @RabbitListener(queues = Queues.SUBMISSION_DISPATCHER)
    public void handleSubmissionEvent(SubmissionEnvelope submissionEnvelope) {

        logger.info("handleSubmissionEvent {}", submissionEnvelope);


        Map<Archive,SubmissionEnvelope> readyToDispatch = dispatcherService
                .assessDispatchReadiness(submissionEnvelope.getSubmission());

        Map<Archive,String> archiveTopic = new HashMap<>();
        archiveTopic.put(Archive.BioSamples,Topics.SAMPLES_PROCESSING);
        archiveTopic.put(Archive.Ena,Topics.ENA_PROCESSING);
        archiveTopic.put(Archive.ArrayExpress,Topics.AE_PROCESSING);


        for (Map.Entry<Archive,SubmissionEnvelope> entry : readyToDispatch.entrySet()){

            Archive archive = entry.getKey();
            SubmissionEnvelope submissionEnvelopeToTransmit = entry.getValue();

            if(!archiveTopic.containsKey(archive)){
                throw new IllegalStateException("Dispatcher does not have topic mapping for archive "+archive+". Processing submission "+submissionEnvelope.getSubmission().getId());
            }

            String targetTopic = archiveTopic.get(archive);

            rabbitMessagingTemplate.convertAndSend(Exchanges.SUBMISSIONS, targetTopic, submissionEnvelope);
            logger.info("sent submission {} to {}", submissionEnvelope.getSubmission().getId(), targetTopic);

            dispatcherService.updateSubmittablesStatusToSubmitted(archive,submissionEnvelope);

        }
    }





}