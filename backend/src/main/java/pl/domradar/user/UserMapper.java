package pl.domradar.user;

import org.mapstruct.Mapper;
import pl.domradar.user.dto.UserDto;

@Mapper
public interface UserMapper {

    UserDto toDto(User user);
}
