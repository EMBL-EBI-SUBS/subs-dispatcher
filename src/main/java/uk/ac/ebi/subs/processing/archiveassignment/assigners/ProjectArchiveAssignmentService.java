package uk.ac.ebi.subs.processing.archiveassignment.assigners;

import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.processing.archiveassignment.ArchiveAssigner;
import uk.ac.ebi.subs.repository.model.Project;

@Component
public class ProjectArchiveAssignmentService implements ArchiveAssigner<Project> {

    @Override
    public Archive assignArchive(Project submittable) {
        return Archive.BioStudies;
    }
}
