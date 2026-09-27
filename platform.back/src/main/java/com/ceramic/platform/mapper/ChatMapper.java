package com.ceramic.platform.mapper;

import com.ceramic.platform.entity.ChatConversation;
import com.ceramic.platform.entity.ChatMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ChatMapper {

    // 顾客视角的未读数 = 客服/AI 发的、顾客还没读的消息条数（不能用 chat_conversations.unread_count，
    // 那是「客服待办数」：顾客发消息 +1，客服回复清零，给顾客看语义正好颠倒）。
    // 注意必须显式列出列名、不能写 cc.*：表里本就有 unread_count 列，重名列会让 MyBatis 映射回旧值。
    @Select("SELECT cc.id, cc.customer_id, cc.support_agent_id, cc.status, cc.subject, cc.priority, cc.last_message, cc.last_message_time, cc.created_at, cc.updated_at, cc.transferred_to_human, cc.ai_summary, (SELECT COUNT(*) FROM chat_messages cm WHERE cm.conversation_id = cc.id AND cm.sender_role IN ('service', 'ai') AND cm.read_by_customer = false) AS unread_count FROM chat_conversations cc WHERE cc.customer_id=#{customerId} ORDER BY cc.updated_at DESC")
    List<ChatConversation> findConversationsByCustomerId(@Param("customerId") Long customerId);

    @Select("SELECT * FROM chat_conversations WHERE id=#{conversationId}")
    ChatConversation findConversationById(@Param("conversationId") Long conversationId);

    @Select("SELECT cc.*, u.nickname AS customer_name FROM chat_conversations cc LEFT JOIN users u ON cc.customer_id = u.id ORDER BY cc.updated_at DESC")
    List<ChatConversation> findAllConversations();

    @Select("SELECT * FROM chat_messages WHERE conversation_id=#{conversationId} ORDER BY timestamp ASC, id ASC")
    List<ChatMessage> findMessagesByConversationId(@Param("conversationId") Long conversationId);

    @Insert("INSERT INTO chat_conversations(customer_id, status, subject, priority, unread_count, created_at, updated_at) VALUES(#{customerId}, #{status}, #{subject}, #{priority}, #{unreadCount}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertConversation(ChatConversation conversation);

    @Insert("INSERT INTO chat_messages(conversation_id, sender_id, sender_role, message, type, timestamp, read_by_support, read_by_customer) VALUES(#{conversationId}, #{senderId}, #{senderRole}, #{message}, #{type}, #{timestamp}, #{readBySupport}, #{readByCustomer})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMessage(ChatMessage message);

    @Update("UPDATE chat_conversations SET unread_count = unread_count + 1, last_message = #{lastMessage}, last_message_time = NOW(), updated_at = NOW() WHERE id = #{conversationId}")
    int increaseUnreadOnNewMessage(@Param("conversationId") Long conversationId, @Param("lastMessage") String lastMessage);

    @Update("UPDATE chat_conversations SET unread_count = 0, last_message = #{lastMessage}, last_message_time = NOW(), updated_at = NOW() WHERE id = #{conversationId}")
    int resetUnreadOnReply(@Param("conversationId") Long conversationId, @Param("lastMessage") String lastMessage);

    @Update("UPDATE chat_conversations SET status = 'closed', updated_at = NOW() WHERE id = #{conversationId}")
    int closeConversation(@Param("conversationId") Long conversationId);

    @Update("UPDATE chat_conversations SET status = #{status}, updated_at = NOW() WHERE id = #{conversationId}")
    int updateConversationStatus(@Param("conversationId") Long conversationId, @Param("status") String status);

    @Update("UPDATE chat_messages SET read_by_customer = true WHERE conversation_id = #{conversationId} AND sender_role IN ('support', 'service', 'ai')")
    int markMessagesAsReadByCustomer(@Param("conversationId") Long conversationId);

    // 删除顾客时级联清理其会话与消息，避免客服列表残留「匿名用户」僵尸会话
    @Delete("DELETE m FROM chat_messages m JOIN chat_conversations cc ON m.conversation_id = cc.id WHERE cc.customer_id = #{customerId}")
    int deleteMessagesByCustomerId(@Param("customerId") Long customerId);

    @Delete("DELETE FROM chat_conversations WHERE customer_id = #{customerId}")
    int deleteConversationsByCustomerId(@Param("customerId") Long customerId);

    @Update("UPDATE chat_conversations SET transferred_to_human = true, support_agent_id = #{agentId}, status = 'pending_human', ai_summary = #{summary}, updated_at = NOW() WHERE id = #{conversationId}")
    int transferToHuman(@Param("conversationId") Long conversationId, @Param("agentId") Long agentId, @Param("summary") String summary);

    @Select("SELECT cc.*, u.nickname AS customer_name FROM chat_conversations cc LEFT JOIN users u ON cc.customer_id = u.id WHERE cc.transferred_to_human = true ORDER BY cc.updated_at DESC")
    List<ChatConversation> findTransferredConversations();
}
