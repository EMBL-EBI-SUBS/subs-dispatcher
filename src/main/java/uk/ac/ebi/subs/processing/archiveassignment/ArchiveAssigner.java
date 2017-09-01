package uk.ac.ebi.subs.processing.archiveassignment;

import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;

public interface ArchiveAssigner<T extends StoredSubmittable> {

    Archive assignArchive(T submittable);
}
