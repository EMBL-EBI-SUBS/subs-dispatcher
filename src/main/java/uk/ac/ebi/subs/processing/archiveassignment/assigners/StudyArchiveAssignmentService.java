package uk.ac.ebi.subs.processing.archiveassignment.assigners;

import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.component.StudyDataType;
import uk.ac.ebi.subs.processing.archiveassignment.ArchiveAssigner;
import uk.ac.ebi.subs.repository.model.Study;

import java.text.MessageFormat;

@Component
public class StudyArchiveAssignmentService implements ArchiveAssigner<Study> {

    @Override
    public Archive assignArchive(Study submittable) {
        StudyDataType studyDataType = submittable.getStudyType();

        Archive archive;

        switch (studyDataType) {
            case Sequencing:
                archive = Archive.Ena;
                break;
            case FunctionalGenomics:
                archive = Archive.ArrayExpress;
                break;
            case Proteomics:
                archive = Archive.Pride;
                break;
            case Metabolomics:
                archive = Archive.Metabolights;
                break;
            default:
                String message = MessageFormat.format("No archive known for study data type {} in study {}", studyDataType, submittable);
                throw new IllegalStateException(message);
        }

        return archive;
    }
}
