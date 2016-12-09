package uk.ac.ebi.subs.dispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.stereotype.Service;

import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.component.SampleRef;
import uk.ac.ebi.subs.data.component.SampleUse;
import uk.ac.ebi.subs.data.submittable.Assay;
import uk.ac.ebi.subs.data.submittable.Sample;
import uk.ac.ebi.subs.data.submittable.Submittable;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.messaging.Queues;
import uk.ac.ebi.subs.messaging.Topics;
import uk.ac.ebi.subs.processing.ProcessingCertificate;
import uk.ac.ebi.subs.processing.ProcessingStatus;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;

import java.util.*;

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
    public void checkSupportingInformationRequirements(SubmissionEnvelope submissionEnvelope) {
        determineSupportingInformationRequired(submissionEnvelope);

        if (!submissionEnvelope.getSupportingSamplesRequired().isEmpty()) {
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

        Map<Archive, Boolean> archiveProcessingRequired = new HashMap<>();
        Arrays.asList(Archive.values()).forEach(a -> archiveProcessingRequired.put(a, false));
        boolean allSubmittablesProcessed = true;

        for (Submittable submittable : submission.allSubmissionItems()) {
            if (submittable.getStatus() == null || !submittable.getStatus().equals(ProcessingStatus.Processed.name())) {
                allSubmittablesProcessed = false;
            }

            if (
                    (submittable.getStatus() != null && submittable.getStatus().equalsIgnoreCase(ProcessingStatus.Processed.name())) ||
                            (submittable.getStatus() != null && submittable.getStatus().equals(ProcessingStatus.Curation.name()))
                    ) {
                continue;
            }

            Archive archive = submittable.getArchive();
            archiveProcessingRequired.put(archive, true);

        }

        String targetTopic = null;

        if (archiveProcessingRequired.get(Archive.BioSamples)) {
            targetTopic = Topics.SAMPLES_PROCESSING;
        } else if (archiveProcessingRequired.get(Archive.Ena)) {
            targetTopic = Topics.ENA_PROCESSING;
        } else if (archiveProcessingRequired.get(Archive.ArrayExpress)) {
            targetTopic = Topics.AE_PROCESSING;
        }

        if (targetTopic != null) {
            rabbitMessagingTemplate.convertAndSend(Exchanges.SUBMISSIONS, targetTopic, submissionEnvelope);
            logger.info("sent submission {} to {}", submission.getId(), targetTopic);
        } else {
            logger.info("no work to do on submission {}", submission.getId());
        }

        if (allSubmittablesProcessed) {
            ProcessingCertificate cert = new ProcessingCertificate();
            cert.setSubmittableId(submission.getId());
            cert.setProcessingStatus(ProcessingStatus.Processed);

            rabbitMessagingTemplate.convertAndSend(
                    Exchanges.SUBMISSIONS,
                    Topics.EVENT_SUBMISSION_STATUS_CHANGE,
                    cert);
        }

    }


    void determineSupportingInformationRequired(SubmissionEnvelope submissionEnvelope) {
        List<Sample> samples = submissionEnvelope.getSubmission().getSamples();
        List<Assay> assays = submissionEnvelope.getSubmission().getAssays();
        Set<SampleRef> suppportingSamplesRequired = submissionEnvelope.getSupportingSamplesRequired();
        List<Sample> supportingSamples = submissionEnvelope.getSupportingSamples();

        for (Assay assay : assays) {
            for (SampleUse sampleUse : assay.getSampleUses()) {
                SampleRef sampleRef = sampleUse.getSampleRef();

                if (suppportingSamplesRequired.contains(sampleRef)) {
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