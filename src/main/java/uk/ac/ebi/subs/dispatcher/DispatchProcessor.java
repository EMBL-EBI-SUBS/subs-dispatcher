package uk.ac.ebi.subs.dispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.data.component.SampleRef;
import uk.ac.ebi.subs.data.component.SampleUse;
import uk.ac.ebi.subs.data.submittable.Assay;
import uk.ac.ebi.subs.data.submittable.Sample;
import uk.ac.ebi.subs.data.submittable.Submittable;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.messaging.Queues;
import uk.ac.ebi.subs.messaging.Topics;

import java.util.List;
import java.util.Set;


@Service
public class DispatchProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DispatchProcessor.class);


    RabbitMessagingTemplate rabbitMessagingTemplate;

    @Autowired
    public DispatchProcessor(RabbitMessagingTemplate rabbitMessagingTemplate, MessageConverter messageConverter) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.rabbitMessagingTemplate.setMessageConverter(messageConverter);
    }

    @RabbitListener(queues = Queues.SUBMISSION_SUPPORTING_INFO)
    public void checkSupportingInformationRequirements(SubmissionEnvelope submissionEnvelope){
        determineSupportingInformationRequired(submissionEnvelope);

        if (!submissionEnvelope.getSupportingSamplesRequired().isEmpty()){
            //TODO refactor this to use a smaller object
            rabbitMessagingTemplate.convertAndSend(
                    Exchanges.SUBMISSIONS,
                    Topics.EVENT_SUBMISSION_NEEDS_SAMPLES,
                    submissionEnvelope
            );
        }
    }

    @RabbitListener(queues = Queues.SUBMISSION_DISPATCHER)
    public void handleSubmissionEvent(SubmissionEnvelope submissionEnvelope) {
        Submission submission = submissionEnvelope.getSubmission();

        logger.info("received submission {}",
                submissionEnvelope.getSubmission().getId());

        /*
        * this is a deliberately simple implementation for prototyping
        * we will need to redo this as we flesh out the system
        * */


        /**
         * for now, assume that anything with an accession is dealt with
         * TODO being accessioned is not the only thing we care about
         */
        long samplesToAccessionCount = submission.getSamples().stream().filter(s -> (!s.isAccessioned())).count();
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

        String targetTopic = null;

        if (samplesToAccessionCount > 0) {
            targetTopic = Topics.SAMPLES_PROCESSING;
        }
        else if (enaCount > 0) {
            targetTopic = Topics.ENA_PROCESSING;
        }
        else if (arrayExpressCount > 0) {
            targetTopic = Topics.AE_PROCESSING;
        }

        if (targetTopic != null) {
            rabbitMessagingTemplate.convertAndSend(Exchanges.SUBMISSIONS,targetTopic, submissionEnvelope);
            logger.info("sent submission {} to {}",submission.getId(),targetTopic);
        }
        else {
            logger.info("completed submission {}",submission.getId());
        }
    }


    void determineSupportingInformationRequired(SubmissionEnvelope submissionEnvelope){
        List<Sample> samples = submissionEnvelope.getSubmission().getSamples();
        List<Assay> assays = submissionEnvelope.getSubmission().getAssays();
        Set<SampleRef> suppportingSamplesRequired = submissionEnvelope.getSupportingSamplesRequired();
        List<Sample> supportingSamples = submissionEnvelope.getSupportingSamples();

        for(Assay assay : assays) {
            for (SampleUse sampleUse : assay.getSampleUses()){
                SampleRef sampleRef = sampleUse.getSampleRef();

                if (suppportingSamplesRequired.contains(sampleRef)){
                    //skip the searching steps if the sample ref is already in the sample required set
                    continue;
                }

                //is the sample in the submission
                Sample s = sampleRef.findMatch(samples);

                if (s == null) {
                    //is the sample already in the supporting information
                    s = sampleRef.findMatch(supportingSamples);
                }

                if (s == null) {
                    // sample referenced is not in the supporting information and is not in the submission, need to fetch it
                    suppportingSamplesRequired.add(sampleRef);
                }

            }
        }
    }
}