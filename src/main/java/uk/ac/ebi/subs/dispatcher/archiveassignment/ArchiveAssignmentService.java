package uk.ac.ebi.subs.dispatcher.archiveassignment;

import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;

public interface ArchiveAssignmentService<T extends StoredSubmittable> {

    Archive assignArchive(T submittable);
}
