package com.confessionverse.backend.service;

import com.confessionverse.backend.dto.UserDTO;
import com.confessionverse.backend.dto.UserDtoMapper;
import com.confessionverse.backend.dto.UserProfileUpdateDTO;
import com.confessionverse.backend.dto.responseDTO.PublicUserProfileDTO;
import com.confessionverse.backend.exception.ResourceNotFoundException;
import com.confessionverse.backend.mapper.EntityDtoMapper;
import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.Confession;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.*;
import com.confessionverse.backend.security.JwtUtil;

import com.confessionverse.backend.security.ownership.OwnableService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService implements OwnableService<User> {

    private final UserRepository userRepository;
    private final EntityDtoMapper mapper;
    private final BoostRepository boostRepository;
    private final ConfessionRepository confessionRepository;
    private final MessageRepository messageRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserDtoMapper userDtoMapper;
    private final SubscriptionEntitlementService subscriptionEntitlementService;
    private final JwtUtil jwtUtil;
    private final ConfessionVoteRepository confessionVoteRepository;
    private final ConfessionReportRepository confessionReportRepository;
    @Value("${app.admin.protected-email:admin@confessionverse.local}")
    private String protectedAdminEmail;

    public UserService(UserRepository userRepository,
                       EntityDtoMapper mapper,
                       BoostRepository boostRepository,
                       ConfessionRepository confessionRepository, MessageRepository messageRepository, SubscriptionRepository subscriptionRepository, ChatRoomRepository chatRoomRepository, UserDtoMapper userDtoMapper, SubscriptionEntitlementService subscriptionEntitlementService, JwtUtil jwtUtil, ConfessionVoteRepository confessionVoteRepository, ConfessionReportRepository confessionReportRepository

    ) {
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.boostRepository = boostRepository;
        this.confessionRepository = confessionRepository;
        this.messageRepository = messageRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.userDtoMapper = userDtoMapper;
        this.subscriptionEntitlementService = subscriptionEntitlementService;
        this.jwtUtil = jwtUtil;
        this.confessionVoteRepository = confessionVoteRepository;
        this.confessionReportRepository = confessionReportRepository;
    }

    public User getAuthenticatedUser(String token) {
        String email = jwtUtil.extractClaim(token, claims -> claims.get("email", String.class));

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    public UserDTO getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
        return userDtoMapper.toUserDto(user);
    }

    public UserDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return userDtoMapper.toUserDto(user);
    }

    public PublicUserProfileDTO getPublicProfileByUsername(String username) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        List<Confession> userPublicConfessions = confessionRepository
                .findByUserUsernameIgnoreCaseOrderByCreatedAtDesc(user.getUsername());

        long totalLikes = userPublicConfessions.stream()
                .map(Confession::getLikeCount)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();

        long totalDislikes = userPublicConfessions.stream()
                .map(Confession::getDislikeCount)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();

        SubscriptionEntitlementService.PlanSnapshot planSnapshot =
                subscriptionEntitlementService.getPlanSnapshot(user.getId());
        return new PublicUserProfileDTO(
                user.getId(),
                user.getUsername(),
                planSnapshot.premium(),
                planSnapshot.planType(),
                null,
                null,
                new PublicUserProfileDTO.Stats(totalLikes, totalDislikes)
        );
    }





    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(mapper::toUserDTO)
                .collect(Collectors.toList());
    }

    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + id));
        return mapper.toUserDTO(user);
    }

    public UserDTO createUser(UserDTO dto) {
        User user = mapper.toUserEntity(dto);
        User saved = userRepository.save(user);
        return mapper.toUserDTO(saved);
    }

    public UserDTO updateUser(Long id, UserDTO dto) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + id));
        existing.setUsername(dto.getUsername());
        existing.setEmail(dto.getEmail());
     ///   existing.setPasswordHash(dto.getPasswordHash());
        if (dto.getRole() != null) {
            Role requestedRole = Role.valueOf(dto.getRole());
            Role currentRole = existing.getRole();
            if (requestedRole != currentRole) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                boolean isAdminCaller = auth != null && auth.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
                if (!isAdminCaller) {
                    throw new IllegalArgumentException("Only admin can change roles");
                }
                if (requestedRole == Role.ADMIN && !isProtectedAdmin(existing)) {
                    throw new IllegalArgumentException("Assigning ADMIN role is restricted");
                }
                existing.setRole(requestedRole);
            }
        }
        existing.setPremium(dto.getPremium());

        User updated = userRepository.save(existing);
        return mapper.toUserDTO(updated);
    }

    @Transactional
    public void deleteUser(Long id) {
        deleteUserByAdmin(id);
    }

    @Transactional
    public void removeUserFromAllChatRooms(Long userId) {
        LinkedHashSet<ChatRoom> rooms = new LinkedHashSet<>();
        rooms.addAll(chatRoomRepository.findAllByParticipants_Id(userId));
        rooms.addAll(chatRoomRepository.findAllByHiddenBy_Id(userId));
        rooms.addAll(chatRoomRepository.findAllByCreator_Id(userId));

        for (ChatRoom room : rooms) {
            if (room.getParticipants() != null) {
                room.getParticipants().removeIf(u -> u != null && userId.equals(u.getId()));
            }
            if (room.getHiddenBy() != null) {
                room.getHiddenBy().removeIf(u -> u != null && userId.equals(u.getId()));
            }
            if (room.getCreator() != null && userId.equals(room.getCreator().getId())) {
                room.setCreator(null);
            }

            boolean hasParticipants = room.getParticipants() != null && !room.getParticipants().isEmpty();
            if (!hasParticipants && room.getCreator() == null) {
                chatRoomRepository.delete(room);
            } else {
                chatRoomRepository.save(room);
            }
        }
    }

    @Transactional
    public void deleteUserByAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + userId));

        if (isProtectedAdmin(user)) {
            throw new IllegalArgumentException("Protected admin account cannot be deleted");
        }

        List<Confession> ownedConfessions = confessionRepository.findByUserId(userId);
        for (Confession confession : ownedConfessions) {
            Long confessionId = confession.getId();
            confessionVoteRepository.deleteAllByConfessionId(confessionId);
            confessionReportRepository.deleteAllByConfessionId(confessionId);
            boostRepository.deleteAllByConfessionId(confessionId);
        }

        confessionVoteRepository.deleteAllByVoterId(userId);
        confessionReportRepository.deleteAllByReporterUserId(userId);
        confessionReportRepository.deleteAllByReporterId(userId);
        boostRepository.deleteAllByUserId(userId);
        confessionRepository.deleteAllByUserId(userId);
        messageRepository.deleteAllBySenderId(userId);
        subscriptionRepository.deleteAllByUserId(userId);

        removeUserFromAllChatRooms(userId);

        userRepository.delete(user);
    }

    private boolean isProtectedAdmin(User user) {
        return user != null
                && user.getEmail() != null
                && protectedAdminEmail != null
                && user.getEmail().equalsIgnoreCase(protectedAdminEmail);
    }
    public UserDTO updateUserByUsername(String username, UserProfileUpdateDTO userDto) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getUsername().equals(userDto.getUsername()) &&
                userRepository.findByUsername(userDto.getUsername()).isPresent()) {
            throw new RuntimeException("Username already taken");
        }

        if (!user.getEmail().equals(userDto.getEmail()) &&
                userRepository.findByEmail(userDto.getEmail()).isPresent()) {
            throw new RuntimeException("Email already taken");
        }

        user.setUsername(userDto.getUsername());
        user.setEmail(userDto.getEmail());
        userRepository.save(user);

        return mapper.toUserDTO(user);
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName(); // we assume this contains the email

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email " + email));
    }

    public boolean isAdminByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> user.getRole() == Role.ADMIN)
                .orElse(false);
    }

    public boolean isAdminByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(user -> user.getRole() == Role.ADMIN)
                .orElse(false);
    }

    public User getUserEntityByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }

    public User getUserEntityByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    public List<User> searchUsers(String query) {
        return userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query);
    }

    @Override
    public Optional<User> getById(Long id) {
        return Optional.ofNullable(userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }

    @Override
    public Class<User> getEntityClass() {
        return User.class;
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
    // Method that returns roles as a list of strings
    public List<String> getRolesByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> Collections.singletonList("ROLE_" + user.getRole().name()))
                .orElse(Collections.emptyList());
    }

    public List<String> getRolesByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(user -> Collections.singletonList("ROLE_" + user.getRole().name()))
                .orElse(Collections.emptyList());
    }

    public boolean isAdmin(String username) {
        return userRepository.findByUsername(username)
                .map(user -> user.getRole() == Role.ADMIN)
                .orElse(false);
    }


    public Long getUserIdByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElse(null);
    }

    public Long getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElse(null);
    }


    public UserDTO updateUserByEmail(String email, UserProfileUpdateDTO dto) {
        // Find the user by email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        // Update fields
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());

        // Save the user to the database
        userRepository.save(user);

        // Convert to DTO and return
        return userDtoMapper.toUserDto(user);
    }

}

