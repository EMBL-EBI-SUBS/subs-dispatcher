package uk.ac.ebi.subs.dispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.FullSubmission;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.component.SampleRef;
import uk.ac.ebi.subs.data.component.SampleUse;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.data.submittable.Assay;
import uk.ac.ebi.subs.data.submittable.Sample;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.messaging.Queues;
import uk.ac.ebi.subs.messaging.Topics;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.repository.FullSubmissionService;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SubmittableRepository;

import java.util.*;

@Service
public class DispatchProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DispatchProcessor.class);

    RabbitMessagingTemplate rabbitMessagingTemplate;


    private List<Class<? extends StoredSubmittable>> submittablesClassList;
    private FullSubmissionService fullSubmissionService;
    private SubmissionRepository submissionRepository;
    private SubmissionStatusRepository submissionStatusRepository;
    private ProcessingStatusRepository processingStatusRepository;
    private List<SubmittableRepository<?>> submissionContentsRepositories;
    private Set<String> processingStatusesToAllow;


    public DispatchProcessor(
            RabbitMessagingTemplate rabbitMessagingTemplate,
            MessageConverter messageConverter,
            FullSubmissionService fullSubmissionService,
            SubmissionRepository submissionRepository,
            SubmissionStatusRepository submissionStatusRepository,
            ProcessingStatusRepository processingStatusRepository,
            List<Class<? extends StoredSubmittable>> submittablesClassList,
            List<SubmittableRepository<?>> submissionContentsRepositories

    ) {
        this.rabbitMessagingTemplate = rabbitMessagingTemplate;
        this.rabbitMessagingTemplate.setMessageConverter(messageConverter);

        this.fullSubmissionService = fullSubmissionService;
        this.submissionRepository = submissionRepository;
        this.submissionStatusRepository = submissionStatusRepository;

        this.submittablesClassList = submittablesClassList;
        this.processingStatusRepository = processingStatusRepository;
        this.submissionContentsRepositories = submissionContentsRepositories;

        processingStatusesToAllow = new HashSet<>();
        processingStatusesToAllow.add(ProcessingStatusEnum.Draft.name());
        processingStatusesToAllow.add(ProcessingStatusEnum.Submitted.name());
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
        logger.info("Marking submittables as submitted for {}",submission.getId());
        processingStatusRepository
                .findBySubmissionId(submission.getId())
                .stream()
                .filter(processingStatus -> processingStatus.getStatus().equals(ProcessingStatusEnum.Draft.name()))
                .forEach(processingStatus -> {
                    processingStatus.setStatus(ProcessingStatusEnum.Submitted);
                    processingStatusRepository.save(processingStatus);
                })
        ;


    }

    @RabbitListener(queues = Queues.SUBMISSION_DELETED_CLEANUP_CONTENTS)
    public void onDeletionCleanupContents(Submission submission) {
//TODO

        for (SubmittableRepository<?> repo : submissionContentsRepositories){
            repo.deleteBySubmissionId(submission.getId());
        }

        processingStatusRepository.deleteBySubmissionId(submission.getId());


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



        List<StoredSubmittable> itemsToProcess = new ArrayList<>();
        submissionContentsRepositories
                .stream()
                .flatMap(repo -> repo.findBySubmissionId(submission.getId()).stream())
                .filter(item ->
                        processingStatusesToAllow.contains(item.getProcessingStatus().getStatus())     )
                .forEach(item -> {
                    archiveProcessingRequired.put(item.getArchive(), true);
                    itemsToProcess.add(item);
                });



        String targetTopic = null;

        if (archiveProcessingRequired.get(Archive.BioSamples)) {
            targetTopic = Topics.SAMPLES_PROCESSING;
            dispatchItems(submissionEnvelope, itemsToProcess, targetTopic, Archive.BioSamples);

        } else if (archiveProcessingRequired.get(Archive.Ena)) {
            targetTopic = Topics.ENA_PROCESSING;
            dispatchItems(submissionEnvelope, itemsToProcess, targetTopic, Archive.Ena);
        } else if (archiveProcessingRequired.get(Archive.ArrayExpress)) {
            targetTopic = Topics.AE_PROCESSING;
            dispatchItems(submissionEnvelope, itemsToProcess, targetTopic, Archive.ArrayExpress);
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
        logger.info("sent submission {} to {}", submissionEnvelope.getSubmission().getId(), targetTopic, itemsToProcess);

        itemsToProcess
                .stream()
                .filter(item -> item.getArchive().equals(targetArchive))
                .filter(item -> processingStatusesToAllow.contains(item.getProcessingStatus().getStatus()))
                .forEach(item -> {
                    item.getProcessingStatus().setStatus(ProcessingStatusEnum.Dispatched);
                    processingStatusRepository.save(item.getProcessingStatus());
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