package uk.ac.ebi.subs.dispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.Repository;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.FullSubmission;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.component.SampleRef;
import uk.ac.ebi.subs.data.component.SampleUse;
import uk.ac.ebi.subs.data.status.ProcessingStatus;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.data.submittable.Assay;
import uk.ac.ebi.subs.data.submittable.Sample;
import uk.ac.ebi.subs.data.submittable.Submittable;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.messaging.Queues;
import uk.ac.ebi.subs.messaging.Topics;
import uk.ac.ebi.subs.processing.ProcessingCertificate;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.repository.FullSubmissionService;
import uk.ac.ebi.subs.repository.SubmissionRepository;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.repos.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.SubmissionStatusRepository;
import uk.ac.ebi.subs.repository.repos.SubmittableRepository;
import uk.ac.ebi.subs.repository.repos.SubmittablesBulkOperations;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DispatchProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DispatchProcessor.class);

    RabbitMessagingTemplate rabbitMessagingTemplate;


    private List<Class> submittablesClassList;
    private FullSubmissionService fullSubmissionService;
    private SubmissionRepository submissionRepository;
    private SubmittablesBulkOperations submittablesBulkOperations;
    private SubmissionStatusRepository submissionStatusRepository;
    private ProcessingStatusRepository processingStatusRepository;
    private List<SubmittableRepository<?>> submissionContentsRepositories;

    @Autowired
    public DispatchProcessor(
            RabbitMessagingTemplate rabbitMessagingTemplate,
            MessageConverter messageConverter,

            FullSubmissionService fullSubmissionService,
            SubmissionRepository submissionRepository,
            SubmittablesBulkOperations submittablesBulkOperations,
            SubmissionStatusRepository submissionStatusRepository,
            ProcessingStatusRepository processingStatusRepository,
            List<Class> submittablesClassList

    ) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.rabbitMessagingTemplate.setMessageConverter(messageConverter);

        this.fullSubmissionService = fullSubmissionService;
        this.submissionRepository = submissionRepository;
        this.submittablesBulkOperations = submittablesBulkOperations;
        this.submissionStatusRepository = submissionStatusRepository;

        this.submittablesClassList = submittablesClassList;
        this.processingStatusRepository = processingStatusRepository;
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
        FullSubmission fullSubmission = fullSubmissionService.fetchOne(submission.getId());

        SubmissionEnvelope submissionEnvelope = new SubmissionEnvelope(fullSubmission);

        Submission refreshedSubmission = submissionRepository.findOne(submission.getId());


        refreshedSubmission.getSubmissionStatus().setStatus(SubmissionStatusEnum.Processing);
        submissionStatusRepository.save(refreshedSubmission.getSubmissionStatus());

        refreshedSubmission.setSubmissionDate(submission.getSubmissionDate());
        submissionRepository.save(refreshedSubmission);


        rabbitMessagingTemplate.convertAndSend(
                Exchanges.SUBMISSIONS,
                Topics.EVENT_SUBMISSION_UPDATED,
                submissionEnvelope
        );

    }

    @RabbitListener(queues = Queues.SUBMISSION_SUBMITTED_CHECK_SUPPORTING_INFO)
    public void onSubmissionCheckSupportingInfoRequirement(Submission submission) {
        logger.info("onSubmissionCheckSupportingInfoRequirement {}", submission);
        FullSubmission fullSubmission = fullSubmissionService.fetchOne(submission.getId());

        SubmissionEnvelope submissionEnvelope = new SubmissionEnvelope(fullSubmission);

        determineSupportingInformationRequired(submissionEnvelope);

        if (!submissionEnvelope.getSupportingSamplesRequired().isEmpty()) {
            //TODO refactor this to use a smaller object?
            rabbitMessagingTemplate.convertAndSend(
                    Exchanges.SUBMISSIONS,
                    Topics.EVENT_SUBMISSION_NEEDS_SAMPLES,
                    submissionEnvelope
            );
        }
    }

    @RabbitListener(queues = Queues.SUBMISSION_SUBMITTED_MARK_SUBMITTABLES)
    public void onSubmissionMarkSubmittablesSubmitted(Submission submission) {

        for (Class submittableClass : submittablesClassList) {
            submittablesBulkOperations.updateProcessingStatusBySubmissionId(
                    submission.getId(),
                    ProcessingStatusEnum.Submitted,
                    ProcessingStatusEnum.Draft,
                    submittableClass
            );
        }

    }

    @RabbitListener(queues = Queues.SUBMISSION_DELETED_CLEANUP_CONTENTS)
    public void onDeletionCleanupContents(Submission submission) {

        for (Class submittableClass : submittablesClassList) {
            submittablesBulkOperations.deleteSubmissionContents(
                    submission.getId(),
                    submittableClass
            );
        }

        submissionStatusRepository.delete(submission.getSubmissionStatus());


    }

    @RabbitListener(queues = Queues.SUBMISSION_DISPATCHER)
    public void handleSubmissionEvent(SubmissionEnvelope submissionEnvelope) {
        logger.info("handleSubmissionEvent {}", submissionEnvelope);
        FullSubmission submission = submissionEnvelope.getSubmission();

        logger.info("received submission {}",
                submissionEnvelope.getSubmission().getId());

        /*
        * this is a deliberately simple implementation for prototyping
        * we will need to redo this as we flesh out the system
        * */


        /**
         * for now, assume that we just have to dispatch things
         * TODO being accessioned is not the only thing we care about
         */

        Map<Archive, Boolean> archiveProcessingRequired = new HashMap<>();
        Arrays.asList(Archive.values()).forEach(a -> archiveProcessingRequired.put(a, false));


        List<StoredSubmittable> itemsToProcess = submissionContentsRepositories
                .stream()
                .flatMap(repo -> repo.findBySubmissionId(submission.getId()).stream())
                .filter(item -> item.getProcessingStatus().getStatus().equals(ProcessingStatusEnum.Processing))
                .map(item -> {archiveProcessingRequired.put(item.getArchive(),true); return item;})
                .collect(Collectors.toList());

        String targetTopic = null;

        if (archiveProcessingRequired.get(Archive.BioSamples)) {
            targetTopic = Topics.SAMPLES_PROCESSING;
            dispatchItems(submissionEnvelope, itemsToProcess, targetTopic, Archive.BioSamples );

        } else if (archiveProcessingRequired.get(Archive.Ena)) {
            targetTopic = Topics.ENA_PROCESSING;
            dispatchItems(submissionEnvelope, itemsToProcess, targetTopic, Archive.Ena );
        } else if (archiveProcessingRequired.get(Archive.ArrayExpress)) {
            targetTopic = Topics.AE_PROCESSING;
            dispatchItems(submissionEnvelope, itemsToProcess, targetTopic, Archive.ArrayExpress );
        }

        if (targetTopic == null) {
            logger.info("no work to do on submission {}", submission.getId());
        }

    }

    private void dispatchItems(
            SubmissionEnvelope submissionEnvelope,
            List<StoredSubmittable> itemsToProcess,
            String targetTopic,
            Archive targetArchive
    ) {
        rabbitMessagingTemplate.convertAndSend(Exchanges.SUBMISSIONS, targetTopic, submissionEnvelope);
        logger.info("sent submission {} to {}", submissionEnvelope.getSubmission().getId(), targetTopic,itemsToProcess);

        itemsToProcess
                .stream()
                .filter(item -> item.getArchive().equals(targetArchive))
                .filter(item -> item.getProcessingStatus().getStatus().equals(ProcessingStatusEnum.Processing))
                .map(item -> {
                    item.getProcessingStatus().setStatus(ProcessingStatusEnum.Dispatched);
                    processingStatusRepository.save(item.getProcessingStatus());
                    return item;
                });

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