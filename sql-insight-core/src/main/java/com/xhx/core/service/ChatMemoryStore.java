package com.xhx.core.service;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * @author master
 */
public interface ChatMemoryStore extends dev.langchain4j.store.memory.chat.ChatMemoryStore {

    @Override
    List<ChatMessage> getMessages(Object o);

    @Override
    void updateMessages(Object o, List<ChatMessage> list);

    @Override
    void deleteMessages(Object o);
}
