package uk.ac.ebi.subs.dispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.submittable.Submission;
import uk.ac.ebi.subs.data.submittable.Submittable;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.messaging.Queues;
import uk.ac.ebi.subs.messaging.Topics;


@Service
public class DispatchProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DispatchProcessor.class);


    RabbitMessagingTemplate rabbitMessagingTemplate;

    @Autowired
    public DispatchProcessor(RabbitMessagingTemplate rabbitMessagingTemplate, MessageConverter messageConverter) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.rabbitMessagingTemplate.setMessageConverter(messageConverter);
    }


    @RabbitListener(queues = {Queues.SUBMISSION_DISPATCHER})
    public void handleSubmissionEvent(Submission submission) {

        logger.info("received submission {} {}",submission.getId(),submission.getLastHandler());

        /*
        * this is a deliberately simple implementation for prototyping
        * we will need to redo this as we flesh out the system
        * */


        /**
         * for now, assume that anything with an accession is dealt with
         */

        long sampleCount = submission.getSamples().stream().filter(s -> (!s.isAccessioned())).count();
        int enaCount = 0;
        int arrayExpressCount = 0;

        for (Submittable submittable : submission.allSubmissionItems()) {
            if (submittable.isAccessioned() ||
                    (submittable.getStatus() != null && submittable.getStatus().equals("processed"))) {
                continue;
            }

            Archive archive = submittable.getArchive();
            if (archive == null){
                archive = Archive.Usi; //default
            }

            switch (archive) {
                case ArrayExpress:
                    arrayExpressCount++;
                    break;
                case Ena:
                    enaCount++;
                    break;
                default:
                    break;
            }
        }

        String targetQueue = null;

        if (sampleCount > 0) {
            targetQueue = Topics.SAMPLES_PROCESSING;
        } else if (enaCount > 0) {
            targetQueue = Topics.ENA_PROCESSING;
        } else if (arrayExpressCount > 0) {
            targetQueue = Topics.AE_PROCESSING;
        }

        if (targetQueue != null) {
            rabbitMessagingTemplate.convertAndSend(Exchanges.SUBMISSIONS,targetQueue, submission);
            logger.info("sent submission {} to {}",submission.getId(),targetQueue);
        }
        else {
            logger.info("completed submission {}",submission.getId());
        }
    }
}