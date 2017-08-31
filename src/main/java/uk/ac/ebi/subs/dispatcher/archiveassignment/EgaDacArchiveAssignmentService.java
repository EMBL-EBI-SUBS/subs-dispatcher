package uk.ac.ebi.subs.dispatcher.archiveassignment;

import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.repository.model.EgaDac;

@Component
public class EgaDacArchiveAssignmentService implements ArchiveAssignmentService<EgaDac> {


    @Override
    public Archive assignArchive(EgaDac submittable) {
        return Archive.Ega;
    }
}
