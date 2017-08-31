package uk.ac.ebi.subs.dispatcher;

import uk.ac.ebi.subs.repository.model.Submission;

public interface SubmissionArchiveAssignmentService {
    void assignArchives(Submission submission);
}
