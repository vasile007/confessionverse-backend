package com.confessionverse.backend.service;

import com.confessionverse.backend.config.PremiumGatesProperties;
import com.confessionverse.backend.dto.ChatRoomDTO;
import com.confessionverse.backend.dto.requestDTO.ChatRoomRequestDTO;
import com.confessionverse.backend.dto.responseDTO.ChatParticipantDTO;
import com.confessionverse.backend.dto.responseDTO.ChatRoomSummaryDTO;
import com.confessionverse.backend.exception.PremiumRoomRequiredException;
import com.confessionverse.backend.exception.ResourceNotFoundException;
import com.confessionverse.backend.mapper.EntityDtoMapper;
import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.ChatRoomMembership;
import com.confessionverse.backend.model.ChatRoomMembershipId;
import com.confessionverse.backend.model.ChatRoomType;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatRoomRepository;
import com.confessionverse.backend.repository.ChatRoomMembershipRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.ownership.Ownable;
import com.confessionverse.backend.security.ownership.OwnableService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Service pentru gestionarea logicii de business asociate ChatRoom-urilor.
 * Implementare OwnableService pentru integrarea cu sistemul de ownership.
 */
@Service
public class ChatRoomService implements OwnableService<ChatRoom> {
    public static final String STANDARD_ROOM_NAME = "Standard";
    public static final String LATE_NIGHT_ROOM_NAME = "Late Night";
    public static final String HEARTBEAT_ROOM_NAME = "Heartbeat";

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMembershipRepository chatRoomMembershipRepository;
    private final EntityDtoMapper mapper;
    private final UserRepository userRepository;
    private final SubscriptionEntitlementService subscriptionEntitlementService;
    private final PremiumGatesProperties premiumGatesProperties;
    private final Environment environment;
    private final Clock clock;

    @Autowired
    public ChatRoomService(ChatRoomRepository chatRoomRepository,
                           ChatRoomMembershipRepository chatRoomMembershipRepository,
                           EntityDtoMapper mapper,
                           UserRepository userRepository,
                           SubscriptionEntitlementService subscriptionEntitlementService,
                           PremiumGatesProperties premiumGatesProperties,
                           Environment environment,
                           Clock clock) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomMembershipRepository = chatRoomMembershipRepository;
        this.mapper = mapper;
        this.userRepository = userRepository;
        this.subscriptionEntitlementService = subscriptionEntitlementService;
        this.premiumGatesProperties = premiumGatesProperties;
        this.environment = environment;
        this.clock = clock;
    }

    /**
     * Obține toate chat room-urile ca listă de DTO-uri.
     */
    public List<ChatRoomDTO> getAllChatRooms() {
        return chatRoomRepository.findAll().stream()
                .map(mapper::toChatRoomDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obține chat room după ID ca DTO.
     * @throws ResourceNotFoundException dacă nu există chat room cu ID-ul dat.
     */
    public ChatRoomDTO getChatRoomById(Long id) {
        ChatRoom chatRoom = getChatRoomEntityById(id);
        return mapper.toChatRoomDTO(chatRoom);
    }

    /**
     * Creează un chat room nou din DTO-ul de request.
     * Asociază participanții, setează createdAt la momentul actual.
     * @throws ResourceNotFoundException dacă unul dintre userii participanți nu există.
     */
    public ChatRoomDTO createChatRoom(ChatRoomRequestDTO chatRoomRequestDTO) {
        Set<User> participants = chatRoomRequestDTO.getParticipantIds().stream()
                .map(id -> userRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + id)))
                .collect(Collectors.toSet());

        ChatRoom chatRoom = mapper.toChatRoomEntity(chatRoomRequestDTO, participants);
        chatRoom.setCreatedAt(LocalDateTime.now());

        ChatRoom saved = chatRoomRepository.save(chatRoom);
        return mapper.toChatRoomDTO(saved);
    }

    /**
     * Actualizează un chat room existent cu noile date din DTO.
     * Actualizează participanții și alte câmpuri dacă e cazul.
     * @throws ResourceNotFoundException dacă chat room-ul sau userii participanți nu există.
     */
    public ChatRoomDTO updateChatRoom(Long id, ChatRoomDTO dto) {
        ChatRoom existing = getChatRoomEntityById(id);

        Set<User> participants = dto.getParticipantIds().stream()
                .map(userId -> userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + userId)))
                .collect(Collectors.toSet());

        existing.setParticipants(participants);

        // TODO: actualizează și alte câmpuri ale chatRoom dacă există

        ChatRoom updated = chatRoomRepository.save(existing);
        return mapper.toChatRoomDTO(updated);
    }

    /**
     * Șterge chat room-ul după ID.
     * @throws ResourceNotFoundException dacă chat room-ul nu există.
     */
    public void deleteChatRoom(Long id) {
        if (!chatRoomRepository.existsById(id)) {
            throw new ResourceNotFoundException("ChatRoom not found with id " + id);
        }
        chatRoomRepository.deleteById(id);
    }

    /**
     * Obține sau creează o cameră privată între doi utilizatori după username.
     * Caută o cameră cu exact acești doi participanți, indiferent de ordinea lor.
     * Dacă nu există, creează una nouă.
     * @throws ResourceNotFoundException dacă oricare dintre utilizatori nu există.
     */
    public ChatRoom getOrCreatePrivateRoomByEmails(String email1, String email2) {
        User user1 = userRepository.findByEmail(email1)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email1));
        User user2 = userRepository.findByEmail(email2)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email2));

        return chatRoomRepository.findPrivateRoom(user1.getId(), user2.getId(), ChatRoomType.DIRECT)
                .orElseGet(() -> {
                    ChatRoom newRoom = new ChatRoom();
                    newRoom.setRoomType(ChatRoomType.DIRECT);
                    newRoom.setParticipants(Set.of(user1, user2));
                    newRoom.setCreatedAt(LocalDateTime.now());
                    return chatRoomRepository.save(newRoom);
                });
    }

    /**
     * Obține entitatea ChatRoom după ID sau aruncă excepție dacă nu există.
     */
    public ChatRoom getChatRoomEntityById(Long id) {
        return chatRoomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ChatRoom not found with id " + id));
    }

    /**
     * Verifică dacă un user este participant într-un chat room.
     * @return true dacă utilizatorul este participant, false altfel.
     */
    public boolean isUserParticipant(Long chatRoomId, String username) {
        Optional<ChatRoom> chatRoomOpt = chatRoomRepository.findById(chatRoomId);
        if (chatRoomOpt.isEmpty()) return false;

        return chatRoomOpt.get().getParticipants().stream()
                .anyMatch(user -> user.getUsername().equals(username));
    }

    /**
     * Implementarea metodei din OwnableService pentru obținerea entității după id.
     */
    @Override
    public Optional<ChatRoom> getById(Long id) {
        return chatRoomRepository.findById(id);
    }

    /**
     * Implementarea metodei din OwnableService pentru a returna clasa entității.
     */
    @Override
    public Class<ChatRoom> getEntityClass() {
        return ChatRoom.class;
    }

    @Transactional
    public ChatRoom getOrCreatePrivateRoomByUsernames(String creatorEmail, String usernameToAdd, String requestedName, ChatRoomType roomType) {
        User creator = userRepository.findByEmail(creatorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + creatorEmail));
        User targetUser = userRepository.findByUsername(usernameToAdd)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + usernameToAdd));

        ChatRoomType requestedType = roomType == null ? ChatRoomType.DIRECT : roomType;
        enforcePremiumRoomAccess(creator, requestedType);
        enforcePremiumRoomAccess(targetUser, requestedType);

        if (creator.getId().equals(targetUser.getId())) {
            throw new IllegalArgumentException("You cannot create a private room with yourself");
        }

        Optional<ChatRoom> existingRoomOpt = chatRoomRepository.findPrivateRoom(
                creator.getId(),
                targetUser.getId(),
                ChatRoomType.DIRECT
        );
        if (existingRoomOpt.isPresent()) {
            ChatRoom existingRoom = existingRoomOpt.get();
            normalizeChatRoom(existingRoom, creator, targetUser, requestedName, requestedType);

            // If requester hid this room earlier, unhide it when reopening.
            existingRoom.getHiddenBy().removeIf(user -> user != null && creator.getId().equals(user.getId()));
            return chatRoomRepository.save(existingRoom);
        }

        ChatRoom newRoom = new ChatRoom();
        newRoom.setCreator(creator);
        newRoom.setRoomType(requestedType);
        newRoom.setUsername((requestedName != null && !requestedName.isBlank()) ? requestedName : targetUser.getUsername());
        newRoom.setParticipants(new LinkedHashSet<>());
        newRoom.setHiddenBy(new HashSet<>());
        newRoom.getParticipants().add(creator);
        newRoom.getParticipants().add(targetUser);
        return chatRoomRepository.save(newRoom);
    }

    @Transactional
    public ChatRoom createGroupRoom(String creatorEmail, String usernameToAdd, String requestedName, ChatRoomType roomType) {
        User creator = userRepository.findByEmail(creatorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + creatorEmail));
        ChatRoomType requestedType = roomType == null ? ChatRoomType.DIRECT : roomType;
        if (requestedType == ChatRoomType.STANDARD) {
            throw new IllegalArgumentException("STANDARD room is system-managed and cannot be created manually.");
        }
        enforcePremiumRoomAccess(creator, requestedType);

        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setCreator(creator);
        chatRoom.setRoomType(requestedType);
        chatRoom.setParticipants(new LinkedHashSet<>());
        chatRoom.setHiddenBy(new HashSet<>());
        chatRoom.getParticipants().add(creator);

        if (usernameToAdd != null && !usernameToAdd.isBlank()) {
            User userToAdd = userRepository.findByUsername(usernameToAdd)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + usernameToAdd));
            enforcePremiumRoomAccess(userToAdd, requestedType);
            chatRoom.getParticipants().add(userToAdd);
        }

        if (requestedName != null && !requestedName.isBlank()) {
            chatRoom.setUsername(requestedName);
        }
        return chatRoomRepository.save(chatRoom);
    }

    public List<ChatRoomSummaryDTO> getVisibleRooms(User currentUser, String scope) {
        ensureBaseRoomsExist();
        ensureStandardMembership(currentUser);
        boolean includeAllForAdmin = currentUser.getRole() == Role.ADMIN && "all".equalsIgnoreCase(scope);
        if (includeAllForAdmin) {
            return chatRoomRepository.findAll().stream()
                    .filter(this::isValidForListing)
                    .map(this::toSummaryDto)
                    .toList();
        }
        return chatRoomMembershipRepository.findAllByUser_IdAndActiveTrueAndHiddenAtIsNull(currentUser.getId())
                .stream()
                .map(ChatRoomMembership::getChatRoom)
                .filter(this::isValidForListing)
                .map(this::toSummaryDto)
                .toList();
    }

    @Transactional
    public void leaveRoom(Long chatRoomId, Long authUserId) {
        if (chatRoomId == null || chatRoomId <= 0) {
            throw new IllegalArgumentException("Invalid roomId");
        }
        chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ResourceNotFoundException.NotFoundException("Chat room not found"));
        userRepository.findById(authUserId)
                .orElseThrow(() -> new AccessDeniedException("Only participants can leave this chat"));
        ChatRoomMembership membership = chatRoomMembershipRepository
                .findByChatRoom_IdAndUser_IdAndActiveTrue(chatRoomId, authUserId)
                .orElseThrow(() -> new AccessDeniedException("Only participants can leave this chat"));
        membership.setActive(false);
        membership.setLeftAt(LocalDateTime.now());
        chatRoomMembershipRepository.save(membership);
    }

    @Transactional
    public void hideMyChat(Long chatRoomId, Long authUserId) {
        chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ResourceNotFoundException.NotFoundException("Chat room not found"));
        ChatRoomMembership membership = chatRoomMembershipRepository.findByChatRoom_IdAndUser_Id(chatRoomId, authUserId)
                .orElseThrow(() -> new ResourceNotFoundException.NotFoundException("Chat room membership not found"));
        membership.setHiddenAt(LocalDateTime.now());
        chatRoomMembershipRepository.save(membership);
    }

    @Transactional
    public void activateMembership(Long chatRoomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ResourceNotFoundException.NotFoundException("Chat room not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + userId));

        ChatRoomMembership membership = chatRoomMembershipRepository.findByChatRoom_IdAndUser_Id(chatRoomId, userId)
                .orElseGet(() -> {
                    ChatRoomMembership newMembership = new ChatRoomMembership();
                    newMembership.setId(new ChatRoomMembershipId(chatRoomId, userId));
                    newMembership.setChatRoom(room);
                    newMembership.setUser(user);
                    newMembership.setJoinedAt(LocalDateTime.now());
                    newMembership.setActive(true);
                    return newMembership;
                });
        if (membership.getJoinedAt() == null) {
            membership.setJoinedAt(LocalDateTime.now());
        }
        membership.setActive(true);
        membership.setLeftAt(null);
        membership.setHiddenAt(null);
        chatRoomMembershipRepository.save(membership);
    }

    public boolean isActiveParticipant(Long chatRoomId, Long userId) {
        if (chatRoomId == null || userId == null) {
            return false;
        }
        return chatRoomMembershipRepository.existsByChatRoom_IdAndUser_IdAndActiveTrue(chatRoomId, userId);
    }

    @Transactional
    public Map<String, Integer> cleanupInvalidAndDuplicatePrivateRooms() {
        List<ChatRoom> allRooms = chatRoomRepository.findAll();
        Map<String, ChatRoom> privatePairIndex = new HashMap<>();
        List<ChatRoom> toDelete = new ArrayList<>();
        int removedInvalid = 0;
        int removedDuplicates = 0;

        for (ChatRoom room : allRooms) {
            normalizeCollections(room);
            room.getParticipants().removeIf(java.util.Objects::isNull);

            if (room.getParticipants().isEmpty()) {
                toDelete.add(room);
                removedInvalid++;
                continue;
            }

            if (room.getParticipants().size() == 2) {
                List<Long> ids = room.getParticipants().stream()
                        .map(User::getId)
                        .filter(java.util.Objects::nonNull)
                        .sorted()
                        .toList();
                if (ids.size() == 2) {
                    String key = ids.get(0) + ":" + ids.get(1);
                    ChatRoom existing = privatePairIndex.get(key);
                    if (existing == null) {
                        privatePairIndex.put(key, room);
                    } else {
                        ChatRoom keep = pickRoomToKeep(existing, room);
                        ChatRoom remove = keep == existing ? room : existing;
                        privatePairIndex.put(key, keep);
                        toDelete.add(remove);
                        removedDuplicates++;
                    }
                }
            }
        }

        if (!toDelete.isEmpty()) {
            chatRoomRepository.deleteAll(toDelete);
        }

        return Map.of(
                "removedInvalidRooms", removedInvalid,
                "removedDuplicatePrivateRooms", removedDuplicates
        );
    }

    @Transactional
    public ChatRoom ensureStandardRoomExists() {
        return ensureRoomTypeExists(ChatRoomType.STANDARD, STANDARD_ROOM_NAME);
    }

    @Transactional
    public Map<ChatRoomType, ChatRoom> ensureBaseRoomsExist() {
        Map<ChatRoomType, ChatRoom> rooms = new HashMap<>();
        rooms.put(ChatRoomType.STANDARD, ensureRoomTypeExists(ChatRoomType.STANDARD, STANDARD_ROOM_NAME));
        rooms.put(ChatRoomType.LATE_NIGHT, ensureRoomTypeExists(ChatRoomType.LATE_NIGHT, LATE_NIGHT_ROOM_NAME));
        rooms.put(ChatRoomType.HEARTBEAT, ensureRoomTypeExists(ChatRoomType.HEARTBEAT, HEARTBEAT_ROOM_NAME));
        return rooms;
    }

    @Transactional
    public boolean ensureStandardMembershipByEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return false;
        }
        return ensureStandardMembership(user);
    }

    @Transactional
    public boolean ensureStandardMembership(User user) {
        return ensureStandardMembership(user, false);
    }

    @Transactional
    public boolean ensureStandardMembership(User user, boolean reactivateIfLeft) {
        if (user == null || user.getId() == null) {
            return false;
        }
        ChatRoom standardRoom = ensureStandardRoomExists();
        return ensureMembership(standardRoom, user, reactivateIfLeft);
    }

    @Transactional
    public int backfillStandardRoomMemberships() {
        ChatRoom standardRoom = ensureStandardRoomExists();

        int addedMemberships = 0;
        for (User user : userRepository.findAll()) {
            if (user == null || user.getId() == null) {
                continue;
            }
            boolean exists = chatRoomMembershipRepository
                    .findByChatRoom_IdAndUser_Id(standardRoom.getId(), user.getId())
                    .isPresent();
            if (!exists) {
                addedMemberships++;
            }
            ensureMembership(standardRoom, user, false);
        }
        return addedMemberships;
    }

    @Transactional
    public boolean ensureMembershipExists(Long chatRoomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ResourceNotFoundException.NotFoundException("Chat room not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + userId));
        return ensureMembership(room, user, false);
    }

    public ChatRoomSummaryDTO toSummaryDto(ChatRoom chatRoom) {
        ChatParticipantDTO creatorDto = null;
        if (chatRoom.getCreator() != null) {
            SubscriptionEntitlementService.PlanSnapshot creatorPlan =
                    subscriptionEntitlementService.getPlanSnapshot(chatRoom.getCreator().getId());
            creatorDto = new ChatParticipantDTO(
                    chatRoom.getCreator().getId(),
                    chatRoom.getCreator().getUsername(),
                    creatorPlan.premium(),
                    creatorPlan.planType()
            );
        }

        List<ChatParticipantDTO> participants = chatRoomMembershipRepository
                .findAllByChatRoom_IdAndActiveTrue(chatRoom.getId())
                .stream()
                .map(ChatRoomMembership::getUser)
                .filter(java.util.Objects::nonNull)
                .map(u -> {
                    SubscriptionEntitlementService.PlanSnapshot participantPlan =
                            subscriptionEntitlementService.getPlanSnapshot(u.getId());
                    return new ChatParticipantDTO(
                            u.getId(),
                            u.getUsername(),
                            participantPlan.premium(),
                            participantPlan.planType()
                    );
                })
                .sorted(Comparator.comparing(ChatParticipantDTO::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        String rawName = chatRoom.getUsername();
        String resolvedName = (rawName != null && !rawName.isBlank())
                ? rawName
                : (creatorDto != null ? creatorDto.getUsername() : null);

        return new ChatRoomSummaryDTO(
                chatRoom.getId(),
                resolvedName,
                chatRoom.getUsername(),
                chatRoom.getRoomType() == null ? ChatRoomType.DIRECT.name() : chatRoom.getRoomType().name(),
                isPremiumRoom(chatRoom.getRoomType()),
                chatRoom.getCreatedAt(),
                creatorDto,
                participants
        );
    }

    @Transactional
    public ChatRoomSummaryDTO randomJoin(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AccessDeniedException("Authentication required");
        }

        ensureBaseRoomsExist();
        ensureStandardMembership(currentUser);

        List<ChatRoomType> eligibleTypes = new ArrayList<>();
        if (subscriptionEntitlementService.isPro(currentUser.getId())) {
            eligibleTypes.add(ChatRoomType.HEARTBEAT);
        }
        if (isLateNightNow()) {
            eligibleTypes.add(ChatRoomType.LATE_NIGHT);
        }
        eligibleTypes.add(ChatRoomType.STANDARD);

        List<ChatRoom> candidates = chatRoomRepository.findAllByRoomTypeInOrderByIdAsc(eligibleTypes);
        ChatRoom selected = candidates.stream()
                .filter(room -> room.getRoomType() == ChatRoomType.HEARTBEAT)
                .findFirst()
                .or(() -> candidates.stream().filter(room -> room.getRoomType() == ChatRoomType.LATE_NIGHT).findFirst())
                .or(() -> candidates.stream().filter(room -> room.getRoomType() == ChatRoomType.STANDARD).findFirst())
                .orElseGet(this::ensureStandardRoomExists);

        enforcePremiumRoomAccess(currentUser, selected);
        activateMembership(selected.getId(), currentUser.getId());
        return toSummaryDto(selected);
    }

    public long getOnlineCount() {
        return chatRoomMembershipRepository.countDistinctActiveUsers();
    }

    public void enforcePremiumRoomAccess(User user, ChatRoom room) {
        if (room == null) {
            return;
        }
        enforcePremiumRoomAccess(user, room.getRoomType());
    }

    public void enforcePremiumRoomAccess(User user, ChatRoomType roomType) {
        if (user == null || roomType == null) {
            return;
        }
        if (isDevProfileActive()) {
            return;
        }
        if (!premiumGatesProperties.isEnabled()) {
            return;
        }
        if (user.getRole() == Role.ADMIN) {
            return;
        }

        boolean premiumRoom = roomType == ChatRoomType.HEARTBEAT || roomType == ChatRoomType.TAROT_DREAMS;
        if (premiumRoom && !subscriptionEntitlementService.isPro(user.getId())) {
            throw new PremiumRoomRequiredException("This room is available for PRO members only.");
        }
    }

    public boolean canUserSeeRoom(ChatRoom chatRoom, User currentUser) {
        if (chatRoom == null || currentUser == null || currentUser.getId() == null) {
            return false;
        }
        Optional<ChatRoomMembership> membership = chatRoomMembershipRepository
                .findByChatRoom_IdAndUser_Id(chatRoom.getId(), currentUser.getId());
        return membership.isPresent()
                && membership.get().isActive()
                && membership.get().getHiddenAt() == null;
    }

    private boolean isValidForListing(ChatRoom chatRoom) {
        return chatRoom != null && chatRoom.getId() != null;
    }

    private void normalizeChatRoom(ChatRoom chatRoom, User creator, User targetUser, String requestedName, ChatRoomType requestedType) {
        normalizeCollections(chatRoom);
        if (chatRoom.getCreator() == null) {
            chatRoom.setCreator(creator);
        }
        if (chatRoom.getRoomType() == null) {
            chatRoom.setRoomType(requestedType == null ? ChatRoomType.DIRECT : requestedType);
        }
        chatRoom.getParticipants().add(creator);
        chatRoom.getParticipants().add(targetUser);
        if ((chatRoom.getUsername() == null || chatRoom.getUsername().isBlank()) && requestedName != null && !requestedName.isBlank()) {
            chatRoom.setUsername(requestedName);
        }
    }

    private void normalizeCollections(ChatRoom chatRoom) {
        if (chatRoom.getParticipants() == null) {
            chatRoom.setParticipants(new LinkedHashSet<>());
        }
        if (chatRoom.getHiddenBy() == null) {
            chatRoom.setHiddenBy(new HashSet<>());
        }
    }

    private boolean isCreator(ChatRoom chatRoom, User currentUser) {
        return chatRoom != null
                && chatRoom.getCreator() != null
                && chatRoom.getCreator().getId() != null
                && chatRoom.getCreator().getId().equals(currentUser.getId());
    }

    private boolean containsUserById(Set<User> users, Long userId) {
        if (users == null || userId == null) {
            return false;
        }
        return users.stream()
                .filter(java.util.Objects::nonNull)
                .map(User::getId)
                .anyMatch(userId::equals);
    }

    private ChatRoom pickRoomToKeep(ChatRoom a, ChatRoom b) {
        LocalDateTime aTime = a.getCreatedAt();
        LocalDateTime bTime = b.getCreatedAt();
        if (aTime != null && bTime != null) {
            return aTime.isAfter(bTime) ? a : b;
        }
        if (aTime != null) return a;
        if (bTime != null) return b;
        Long aId = a.getId() == null ? Long.MAX_VALUE : a.getId();
        Long bId = b.getId() == null ? Long.MAX_VALUE : b.getId();
        return aId <= bId ? a : b;
    }

    private boolean isDevProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch("dev"::equalsIgnoreCase);
    }

    private ChatRoom ensureRoomTypeExists(ChatRoomType roomType, String roomName) {
        List<ChatRoom> rooms = chatRoomRepository.findAllByRoomTypeOrderByIdAsc(roomType);
        if (!rooms.isEmpty()) {
            ChatRoom existing = rooms.get(0);
            normalizeCollections(existing);
            if (existing.getUsername() == null || existing.getUsername().isBlank()) {
                existing.setUsername(roomName);
                return chatRoomRepository.save(existing);
            }
            return existing;
        }

        ChatRoom room = new ChatRoom();
        room.setRoomType(roomType);
        room.setUsername(roomName);
        room.setParticipants(new LinkedHashSet<>());
        room.setHiddenBy(new HashSet<>());
        return chatRoomRepository.save(room);
    }

    private boolean ensureMembership(ChatRoom room, User user, boolean reactivateIfLeft) {
        Optional<ChatRoomMembership> existingMembership = chatRoomMembershipRepository
                .findByChatRoom_IdAndUser_Id(room.getId(), user.getId());

        if (existingMembership.isEmpty()) {
            ChatRoomMembership membership = new ChatRoomMembership();
            membership.setId(new ChatRoomMembershipId(room.getId(), user.getId()));
            membership.setChatRoom(room);
            membership.setUser(user);
            membership.setActive(true);
            membership.setJoinedAt(LocalDateTime.now());
            membership.setLeftAt(null);
            membership.setHiddenAt(null);
            chatRoomMembershipRepository.save(membership);
            return true;
        }

        ChatRoomMembership membership = existingMembership.get();
        boolean changed = false;
        if (membership.getJoinedAt() == null) {
            membership.setJoinedAt(LocalDateTime.now());
            changed = true;
        }
        if (reactivateIfLeft && !membership.isActive()) {
            membership.setActive(true);
            membership.setLeftAt(null);
            membership.setHiddenAt(null);
            changed = true;
        }
        if (changed) {
            chatRoomMembershipRepository.save(membership);
        }
        return changed;
    }

    private boolean isLateNightNow() {
        LocalTime now = LocalTime.now(clock);
        int hour = now.getHour();
        return hour >= 22 || hour <= 5;
    }

    private boolean isPremiumRoom(ChatRoomType roomType) {
        return roomType == ChatRoomType.HEARTBEAT || roomType == ChatRoomType.TAROT_DREAMS;
    }

}



