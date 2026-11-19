package pl.domradar.notification;

import org.mapstruct.Mapper;
import pl.domradar.notification.dto.NotificationDto;

@Mapper
public interface NotificationMapper {

    NotificationDto toDto(Notification notification);
}
