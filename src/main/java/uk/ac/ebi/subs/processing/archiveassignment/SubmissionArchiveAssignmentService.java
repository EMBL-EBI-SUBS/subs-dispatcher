package uk.ac.ebi.subs.processing.archiveassignment;

import uk.ac.ebi.subs.repository.model.Submission;

public interface SubmissionArchiveAssignmentService {
    void assignArchives(Submission submission);
}
