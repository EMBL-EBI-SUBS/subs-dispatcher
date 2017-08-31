package uk.ac.ebi.subs.dispatcher.archiveassignment;

import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.repository.model.Project;

@Component
public class ProjectArchiveAssignmentService implements ArchiveAssignmentService<Project> {

    @Override
    public Archive assignArchive(Project submittable) {
        return Archive.BioStudies;
    }
}
