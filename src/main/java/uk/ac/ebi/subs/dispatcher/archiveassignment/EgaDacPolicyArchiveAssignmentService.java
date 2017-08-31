package uk.ac.ebi.subs.dispatcher.archiveassignment;

import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.repository.model.EgaDacPolicy;

@Component
public class EgaDacPolicyArchiveAssignmentService implements ArchiveAssignmentService<EgaDacPolicy> {

    @Override
    public Archive assignArchive(EgaDacPolicy submittable) {
        return Archive.Ega;
    }
}
