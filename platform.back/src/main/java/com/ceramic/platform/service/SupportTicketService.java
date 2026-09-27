package com.ceramic.platform.service;

import com.ceramic.platform.entity.SupportTicket;
import com.ceramic.platform.mapper.SupportTicketMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SupportTicketService {
    private final SupportTicketMapper supportTicketMapper;

    public SupportTicketService(SupportTicketMapper supportTicketMapper) {
        this.supportTicketMapper = supportTicketMapper;
    }

    public List<SupportTicket> list(Long userId) {
        return userId == null ? supportTicketMapper.findAll() : supportTicketMapper.findByUserId(userId);
    }

    public SupportTicket create(SupportTicket ticket) {
        if (ticket.getTitle() == null || ticket.getTitle().isBlank()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (ticket.getContent() == null || ticket.getContent().isBlank()) {
            throw new IllegalArgumentException("内容不能为空");
        }
        if (ticket.getPriority() == null || ticket.getPriority().isBlank()) {
            ticket.setPriority("medium");
        }
        ticket.setStatus("OPEN");
        supportTicketMapper.insert(ticket);
        return ticket;
    }

    public void reply(Long id, SupportTicket ticket) {
        ticket.setId(id);
        if (ticket.getStatus() == null || ticket.getStatus().isBlank()) {
            ticket.setStatus("REPLIED");
        }
        supportTicketMapper.reply(ticket);
    }

    public void updatePriority(Long id, String priority) {
        supportTicketMapper.updatePriority(id, priority);
    }

    public void updateStatus(Long id, String status) {
        supportTicketMapper.updateStatus(id, status);
    }
}
