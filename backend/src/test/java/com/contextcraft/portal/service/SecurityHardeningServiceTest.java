package com.contextcraft.portal.service;

import com.contextcraft.portal.entity.Business;
import com.contextcraft.portal.entity.Task;
import com.contextcraft.portal.entity.User;
import com.contextcraft.portal.repository.BusinessRepository;
import com.contextcraft.portal.repository.DepartmentRepository;
import com.contextcraft.portal.repository.RoleRepository;
import com.contextcraft.portal.repository.TaskAssignmentRepository;
import com.contextcraft.portal.repository.TaskHistoryRepository;
import com.contextcraft.portal.repository.TaskRepository;
import com.contextcraft.portal.repository.TelegramUserRepository;
import com.contextcraft.portal.repository.UserPhoneRepository;
import com.contextcraft.portal.repository.UserRepository;
import com.contextcraft.portal.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityHardeningServiceTest {

    @Test
    void loginByEmailAndPortalNameUsesPasswordEncoderForHashedPasswords() {
        UserRepository userRepository = mock(UserRepository.class);
        UserPhoneRepository userPhoneRepository = mock(UserPhoneRepository.class);
        TelegramUserRepository telegramUserRepository = mock(TelegramUserRepository.class);
        BusinessRepository businessRepository = mock(BusinessRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        UserService service = new UserService(
                userRepository,
                userPhoneRepository,
                telegramUserRepository,
                businessRepository,
                roleRepository,
                userRoleRepository,
                departmentRepository,
                passwordEncoder
        );

        Business business = new Business();
        business.setPortalPassword("$2a$10$encodedhash");

        User user = new User();
        user.setBusiness(business);
        user.setStatus("ACTIVE");

        when(userRepository.findByEmailAndBusinessName("user@example.com", "Acme"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "$2a$10$encodedhash")).thenReturn(true);

        User result = service.loginByEmailAndPortalName("user@example.com", "Acme", "secret");

        assertSame(user, result);
        verify(passwordEncoder).matches("secret", "$2a$10$encodedhash");
    }

    @Test
    void createTaskRejectsBlankTitle() {
        TaskRepository taskRepository = mock(TaskRepository.class);
        TaskAssignmentRepository taskAssignmentRepository = mock(TaskAssignmentRepository.class);
        TaskHistoryRepository taskHistoryRepository = mock(TaskHistoryRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        BusinessRepository businessRepository = mock(BusinessRepository.class);

        TaskService service = new TaskService(taskRepository, taskAssignmentRepository, taskHistoryRepository, userRepository, businessRepository);

        UUID businessId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        Business business = new Business();
        User creator = new User();

        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));

        assertThrows(IllegalArgumentException.class, () ->
                service.createTask(businessId, creatorId, "   ", "desc", null, "MEDIUM", null));
    }
}
