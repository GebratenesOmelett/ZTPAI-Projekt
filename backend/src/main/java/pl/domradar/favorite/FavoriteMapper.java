package pl.domradar.favorite;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pl.domradar.favorite.dto.FavoriteDto;

@Mapper
public interface FavoriteMapper {

    @Mapping(target = "propertyId", source = "property.id")
    @Mapping(target = "title", source = "property.title")
    @Mapping(target = "propertyType", source = "property.propertyType")
    @Mapping(target = "transactionType", source = "property.transactionType")
    @Mapping(target = "city", source = "property.city.name")
    @Mapping(target = "district", source = "property.district.name")
    @Mapping(target = "area", source = "property.area")
    @Mapping(target = "rooms", source = "property.rooms")
    @Mapping(target = "price", source = "property.currentPrice")
    @Mapping(target = "pricePerM2", source = "property.pricePerM2")
    @Mapping(target = "priceDropped", source = "property.priceDropped")
    @Mapping(target = "mainPhotoUrl", source = "property.mainPhotoUrl")
    @Mapping(target = "status", source = "property.status")
    @Mapping(target = "archived", source = "property.archived")
    FavoriteDto toDto(Favorite favorite);
}
