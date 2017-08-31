package uk.ac.ebi.subs.processing.archiveassignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.processing.dispatcher.SubmissionEnvelopeService;
import uk.ac.ebi.subs.processing.archiveassignment.assigners.StoredSubmittableArchiveAssignmentService;
import uk.ac.ebi.subs.repository.model.ProcessingStatus;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;

@Component
public class SubmissionArchiveAssignmentServiceImpl implements SubmissionArchiveAssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionArchiveAssignmentServiceImpl.class);

    @Autowired
    StoredSubmittableArchiveAssignmentService storedSubmittableArchiveAssignmentService;

    @Autowired
    private SubmissionEnvelopeService submissionEnvelopeService;

    @Autowired
    private ProcessingStatusRepository processingStatusRepository;

    @Override
    public void assignArchives(Submission submission) {
        logger.info("assigning archives for submission {}", submission);

        submissionEnvelopeService.submissionContents(submission.getId()).forEach(storedSubmittable -> {
            Archive archive = storedSubmittableArchiveAssignmentService.assignArchive(storedSubmittable);


            ProcessingStatus processingStatus = storedSubmittable.getProcessingStatus();
            processingStatus.setArchive(archive.name());
            processingStatusRepository.save(processingStatus);
        });
    }

}
