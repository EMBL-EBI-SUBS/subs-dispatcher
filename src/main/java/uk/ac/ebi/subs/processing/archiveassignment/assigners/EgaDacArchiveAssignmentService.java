package uk.ac.ebi.subs.processing.archiveassignment.assigners;

import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.processing.archiveassignment.ArchiveAssigner;
import uk.ac.ebi.subs.repository.model.EgaDac;

@Component
public class EgaDacArchiveAssignmentService implements ArchiveAssigner<EgaDac> {


    @Override
    public Archive assignArchive(EgaDac submittable) {
        return Archive.Ega;
    }
}
