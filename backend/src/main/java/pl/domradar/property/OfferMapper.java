package pl.domradar.property;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pl.domradar.property.dto.OfferDto;

@Mapper
public interface OfferMapper {

    @Mapping(target = "propertyId", source = "property.id")
    @Mapping(target = "sourceId", source = "source.id")
    @Mapping(target = "sourceCode", source = "source.code")
    @Mapping(target = "sourceName", source = "source.name")
    OfferDto toDto(Offer offer);

    OfferDto.PricePointDto toDto(PriceHistory history);
}
