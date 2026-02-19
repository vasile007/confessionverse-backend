package com.confessionverse.backend.mapper;

import com.confessionverse.backend.dto.*;
import com.confessionverse.backend.dto.requestDTO.*;
import com.confessionverse.backend.dto.responseDTO.*;
import com.confessionverse.backend.model.*;
import com.confessionverse.backend.service.SubscriptionEntitlementService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EntityDtoMapper {
    private final SubscriptionEntitlementService subscriptionEntitlementService;

    public EntityDtoMapper(SubscriptionEntitlementService subscriptionEntitlementService) {
        this.subscriptionEntitlementService = subscriptionEntitlementService;
    }

    // User
    public UserDTO toUserDTO(User user) {
        if (user == null) return null;
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole().name());
        SubscriptionEntitlementService.PlanSnapshot planSnapshot = subscriptionEntitlementService.getPlanSnapshot(user.getId());
        dto.setPremium(planSnapshot.premium());
        dto.setPlanType(planSnapshot.planType());
        return dto;
    }


    public User toUserEntity(UserDTO dto) {
        if(dto == null) return null;
        User user = new User();
        user.setId(dto.getId());
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
     //   user.setPasswordHash(dto.getPasswordHash());
        user.setRole(dto.getRole() == null ? null : Role.valueOf(dto.getRole()));
        user.setPremium(dto.getPremium());
        // Relații nu setăm aici, se gestionează separat
        return user;
    }

    // Confession
    public ConfessionDTO toConfessionDTO(Confession c) {
        if(c == null) return null;
        ConfessionDTO dto = new ConfessionDTO();
        dto.setId(c.getId());
        dto.setContent(c.getContent());
        dto.setUserId(c.getUser() == null ? null : c.getUser().getId());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setBoostIds(c.getBoosts() == null ? null :
                c.getBoosts().stream().map(Boost::getId).collect(Collectors.toSet()));
        return dto;
    }

    public Confession toConfessionEntity(ConfessionDTO dto) {
        if(dto == null) return null;
        Confession c = new Confession();
        c.setId(dto.getId());
        c.setContent(dto.getContent());
        // user se setează în serviciu/controller separat
        return c;
    }
    public Confession toEntity(ConfessionRequestDTO dto, User user) {
        Confession confession = new Confession();
        confession.setContent(dto.getContent());
        confession.setUser(user);
        return confession;
    }
    public ConfessionResponseDTO toConfessionResponseDTO(Confession confession) {
        if (confession == null) return null;
        ConfessionResponseDTO dto = new ConfessionResponseDTO();
        dto.setId(confession.getId());
        dto.setContent(confession.getContent());

        // 🔹 Adăugăm timestamp formatat frumos (dacă există)
        if (confession.getCreatedAt() != null) {
            dto.setTimestamp(confession.getCreatedAt().toString());
        }

        if (confession.getUser() != null) {
            dto.setUserId(confession.getUser().getId());
            dto.setUsername(confession.getUser().getUsername());
            UserSummaryDTO authorInfo = toUserSummaryDTO(confession.getUser());
            dto.setUser(authorInfo);
            dto.setOwner(authorInfo);
            dto.setAuthorInfo(authorInfo);
            boolean premiumHighlight = Boolean.TRUE.equals(authorInfo.getPremium());
            dto.setPremiumHighlight(premiumHighlight);
            dto.setHighlighted(premiumHighlight);
            dto.setIsPremium(premiumHighlight);
        } else {
            dto.setPremiumHighlight(false);
            dto.setHighlighted(false);
            dto.setIsPremium(false);
        }
        dto.setHidden(Boolean.TRUE.equals(confession.getHidden()));

        //  Autorul e mereu anonim pentru confesiunile publice
        dto.setAuthor("Anonim");

        return dto;
    }


    /*
      public ConfessionResponseDTO toConfessionResponseDTO(Confession confession) {
        if (confession == null) return null;
        ConfessionResponseDTO dto = new ConfessionResponseDTO();
        dto.setId(confession.getId());
        dto.setContent(confession.getContent());
        dto.setTimestamp(confession.getCreatedAt().toString());
        dto.setUsername(confession.getUser() != null ? confession.getUser().getUsername() : null);
        return dto;
    }
     */





    // ChatRoom
    public ChatRoomDTO toChatRoomDTO(ChatRoom cr) {
        if(cr == null) return null;
        ChatRoomDTO dto = new ChatRoomDTO();
        dto.setId(cr.getId());
        dto.setCreatedAt(cr.getCreatedAt());
        dto.setRoomType(cr.getRoomType() == null ? null : cr.getRoomType().name());
        dto.setParticipantIds(cr.getParticipants() == null ? null :
                cr.getParticipants().stream().map(User::getId).collect(Collectors.toSet()));
        dto.setMessageIds(cr.getMessages() == null ? null :
                cr.getMessages().stream().map(Message::getId).collect(Collectors.toSet()));
        return dto;
    }

    public ChatRoom toChatRoomEntity(ChatRoomDTO dto) {
        if(dto == null) return null;
        ChatRoom cr = new ChatRoom();
        cr.setId(dto.getId());
        if (dto.getRoomType() != null) {
            cr.setRoomType(ChatRoomType.valueOf(dto.getRoomType()));
        }
        // participanții și mesajele se setează separat în serviciu/controller
        return cr;
    }


    public Message toMessageEntity(MessageDTO dto) {
        if(dto == null) return null;
        Message m = new Message();
        m.setId(dto.getId());
        m.setContent(dto.getContent());
        // chatRoom și sender se setează separat
        return m;
    }

    // Subscription
    public SubscriptionDTO toSubscriptionDTO(Subscription s) {
        if(s == null) return null;
        SubscriptionDTO dto = new SubscriptionDTO();
        dto.setId(s.getId());
        dto.setPlanType(s.getPlanType());
        dto.setStartDate(s.getStartDate());
        dto.setEndDate(s.getEndDate());
        dto.setUserId(s.getUser() == null ? null : s.getUser().getId());
        return dto;
    }

    public Subscription toSubscriptionEntity(SubscriptionDTO dto) {
        if(dto == null) return null;
        Subscription s = new Subscription();
        s.setId(dto.getId());
        s.setPlanType(dto.getPlanType());
        s.setStartDate(dto.getStartDate());
        s.setEndDate(dto.getEndDate());
        // user se setează separat
        return s;
    }

    // Conversie entity -> DTO
    public BoostDTO toBoostDTO(Boost b) {
        if (b == null) return null;
        BoostDTO dto = new BoostDTO();
        dto.setId(b.getId());
        dto.setConfessionId(b.getConfession() == null ? null : b.getConfession().getId());
        dto.setUserId(b.getUser() == null ? null : b.getUser().getId());
        dto.setBoostType(b.getBoostType() == null ? null : b.getBoostType().name()); // enum to String
        dto.setDate(b.getDate());
        return dto;
    }

    // Conversie DTO -> entity
    public Boost toBoostEntity(BoostDTO dto) {
        if (dto == null) return null;
        Boost b = new Boost();
        b.setId(dto.getId());
        b.setBoostType(dto.getBoostType() == null ? null : BoostType.valueOf(dto.getBoostType())); // String to enum
        // user și confession se setează separat, la nevoie
        return b;
    }

    // Conversie BoostRequestDTO + user + confession -> Boost entity (ex: când creezi boost nou)
    public Boost toEntity(BoostRequestDTO dto, User user, Confession confession) {
        if (dto == null) return null;
        Boost boost = new Boost();
        boost.setBoostType(dto.getBoostType() == null ? null : BoostType.valueOf(dto.getBoostType()));
        boost.setUser(user);
        boost.setConfession(confession);
        boost.setDate(java.time.LocalDateTime.now());
        return boost;
    }

    // Conversie entity Boost -> BoostResponseDTO (pentru răspuns API)
    public BoostResponseDTO toResponseDto(Boost boost) {
        if (boost == null) return null;
        return BoostResponseDTO.builder()
                .id(boost.getId())
                .boostType(boost.getBoostType() == null ? null : boost.getBoostType().name())
                .username(boost.getUser() == null ? null : boost.getUser().getUsername())
                .confessionId(boost.getConfession() == null ? null : boost.getConfession().getId())
                .date(boost.getDate())
                .build();

    }


    // User
    public UserResponseDTO toUserResponseDTO(User user) {
        if (user == null) return null;
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole() == null ? null : user.getRole().name());
        SubscriptionEntitlementService.PlanSnapshot planSnapshot = subscriptionEntitlementService.getPlanSnapshot(user.getId());
        dto.setPremium(planSnapshot.premium());
        dto.setPlanType(planSnapshot.planType());
        return dto;
    }

    public User toUserEntity(UserRequestDTO dto) {
        if (dto == null) return null;
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPasswordHash(dto.getPassword()); // hash în serviciu, aici doar setăm
        user.setRole(dto.getRole() == null ? null : Role.valueOf(dto.getRole()));
        user.setPremium(dto.getPremium());
        return user;
    }

    // ChatRoom
    public ChatRoomResponseDTO toChatRoomResponseDTO(ChatRoom chatRoom) {
        if (chatRoom == null) return null;
        ChatRoomResponseDTO dto = new ChatRoomResponseDTO();
        dto.setId(chatRoom.getId());
        dto.setCreatedAt(chatRoom.getCreatedAt());
        dto.setParticipantIds(chatRoom.getParticipants() == null ? null :
                chatRoom.getParticipants().stream().map(User::getId).collect(Collectors.toSet()));
        dto.setMessageIds(chatRoom.getMessages() == null ? null :
                chatRoom.getMessages().stream().map(Message::getId).collect(Collectors.toSet()));
        return dto;
    }

    public ChatRoom toChatRoomEntity(ChatRoomRequestDTO dto, Set<User> participants) {
        if (dto == null) return null;
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setParticipants(participants);
        return chatRoom;
    }



    // Message
    public MessageDTO toMessageDTO(Message message) {
        if (message == null) return null;

        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setContent(message.getContent());
        dto.setTimestamp(message.getTimestamp());
        dto.setChatRoomId(message.getChatRoom().getId());
        dto.setSenderId(message.getSender().getId());
        return dto;
    }

    public Message toMessageEntity(MessageRequestDTO dto, ChatRoom chatRoom, User sender) {
        if (dto == null) return null;
        Message message = new Message();
        message.setContent(dto.getContent());
        message.setChatRoom(chatRoom);
        message.setSender(sender);
        return message;
    }

    // Subscription
    public SubscriptionResponseDTO toSubscriptionResponseDTO(Subscription subscription) {
        if (subscription == null) return null;
        SubscriptionResponseDTO dto = new SubscriptionResponseDTO();
        dto.setId(subscription.getId());
        dto.setPlanType(subscription.getPlanType());
        dto.setStatus(subscription.getStatus());
        dto.setStartDate(subscription.getStartDate());
        dto.setEndDate(subscription.getEndDate());
        dto.setUserId(subscription.getUser() == null ? null : subscription.getUser().getId());
        dto.setCurrentPeriodStart(subscription.getCurrentPeriodStart());
        dto.setCurrentPeriodEnd(subscription.getCurrentPeriodEnd());
        dto.setCancelAtPeriodEnd(subscription.getCancelAtPeriodEnd());
        dto.setLastPaymentAt(subscription.getLastPaymentAt());
        dto.setLastInvoiceId(subscription.getLastInvoiceId());
        dto.setStripeCustomerIdShort(shortStripeId(subscription.getStripeCustomerId()));
        dto.setStripeSubscriptionIdShort(shortStripeId(subscription.getStripeSubscriptionId()));
        return dto;
    }

    public Subscription toSubscriptionEntity(SubscriptionRequestDTO dto, User user) {
        if (dto == null) return null;
        Subscription subscription = new Subscription();
        subscription.setPlanType(dto.getPlanType());
        subscription.setStartDate(dto.getStartDate());
        subscription.setEndDate(dto.getEndDate());
        subscription.setUser(user);
        return subscription;
    }
    // Subscription
    public Subscription toEntity(SubscriptionRequestDTO dto, User user) {
        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setPlanType(dto.getPlanType());
        sub.setStartDate(dto.getStartDate() != null ? dto.getStartDate() : LocalDate.now());
        sub.setEndDate(dto.getEndDate() != null ? dto.getEndDate() : sub.getStartDate().plusMonths(1));
        return sub;
    }

    public SubscriptionResponseDTO toResponseDto(Subscription subscription) {
        SubscriptionResponseDTO dto = new SubscriptionResponseDTO();
        dto.setId(subscription.getId());
        dto.setUserId(subscription.getUser() == null ? null : subscription.getUser().getId());
        dto.setUsername(subscription.getUser() == null ? null : subscription.getUser().getUsername());
        dto.setEmail(subscription.getUser() == null ? null : subscription.getUser().getEmail());
        dto.setPlanType(subscription.getPlanType());
        dto.setStatus(subscription.getStatus());
        dto.setStartDate(subscription.getStartDate());
        dto.setEndDate(subscription.getEndDate());
        dto.setCurrentPeriodStart(subscription.getCurrentPeriodStart());
        dto.setCurrentPeriodEnd(subscription.getCurrentPeriodEnd());
        dto.setCancelAtPeriodEnd(subscription.getCancelAtPeriodEnd());
        dto.setLastPaymentAt(subscription.getLastPaymentAt());
        dto.setLastInvoiceId(subscription.getLastInvoiceId());
        dto.setStripeCustomerIdShort(shortStripeId(subscription.getStripeCustomerId()));
        dto.setStripeSubscriptionIdShort(shortStripeId(subscription.getStripeSubscriptionId()));
        return dto;
    }

    private String shortStripeId(String stripeId) {
        if (stripeId == null || stripeId.isBlank()) {
            return null;
        }
        if (stripeId.length() <= 12) {
            return stripeId;
        }
        return stripeId.substring(0, 6) + "..." + stripeId.substring(stripeId.length() - 4);
    }
    public Confession toEntity(ConfessionDTO dto, User user) {
        Confession c = new Confession();
        c.setContent(dto.getContent());
        c.setUser(user);
        return c;
    }

    public UserSummaryDTO toUserSummaryDTO(User user) {
        if (user == null) {
            return null;
        }
        SubscriptionEntitlementService.PlanSnapshot planSnapshot = subscriptionEntitlementService.getPlanSnapshot(user.getId());
        return new UserSummaryDTO(
                user.getId(),
                user.getUsername(),
                planSnapshot.premium(),
                planSnapshot.planType()
        );
    }
}
