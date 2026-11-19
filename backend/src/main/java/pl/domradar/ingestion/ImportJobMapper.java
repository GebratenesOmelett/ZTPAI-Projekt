package pl.domradar.ingestion;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pl.domradar.ingestion.dto.ImportJobDto;

@Mapper
public interface ImportJobMapper {

    @Mapping(target = "sourceId", source = "source.id")
    @Mapping(target = "sourceCode", source = "source.code")
    @Mapping(target = "sourceName", source = "source.name")
    ImportJobDto toDto(ImportJob job);
}
