package pl.domradar.property.dictionary;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.property.dictionary.dto.CityDto;
import pl.domradar.property.dictionary.dto.DistrictDto;
import pl.domradar.property.dictionary.dto.FeatureDto;
import pl.domradar.property.dictionary.dto.SourceOptionDto;
import pl.domradar.source.SourceRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictionaryService {

    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;
    private final FeatureRepository featureRepository;
    private final SourceRepository sourceRepository;

    @Transactional(readOnly = true)
    public List<CityDto> cities() {
        return cityRepository.findAllByOrderByNameAsc().stream()
                .map(c -> new CityDto(c.getId(), c.getName(), c.getSlug(), c.getLat(), c.getLon())).toList();
    }

    @Transactional(readOnly = true)
    public List<DistrictDto> districts(Long cityId) {
        List<District> districts = cityId == null ? districtRepository.findAllByOrderByCityNameAscNameAsc()
                : districtRepository.findByCityIdOrderByNameAsc(cityId);
        return districts.stream()
                .map(d -> new DistrictDto(d.getId(), d.getCity().getId(), d.getName(), d.getSlug(), d.getLat(), d.getLon()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeatureDto> features() {
        return featureRepository.findAllByOrderByNameAsc().stream().map(f -> new FeatureDto(f.getCode(), f.getName())).toList();
    }

    @Transactional(readOnly = true)
    public List<SourceOptionDto> sources() {
        return sourceRepository.findAllByOrderByNameAsc().stream()
                .map(s -> new SourceOptionDto(s.getCode(), s.getName())).toList();
    }
}
