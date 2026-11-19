package pl.domradar.source;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import pl.domradar.source.dto.SourceDto;
import pl.domradar.source.dto.SourceRequest;

@Mapper
public interface SourceMapper {

    SourceDto toDto(Source source);

    void update(@MappingTarget Source source, SourceRequest request);
}
