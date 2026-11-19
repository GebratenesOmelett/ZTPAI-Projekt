package pl.domradar.savedsearch;

import org.mapstruct.Mapper;
import pl.domradar.savedsearch.dto.SavedSearchDto;

@Mapper
public interface SavedSearchMapper {

    SavedSearchDto toDto(SavedSearch savedSearch);
}
