package com.spit.lanchat.common.dto;

import java.util.ArrayList;
import java.util.List;

public class PendingSyncResponse {
    private List<DirectMessagePush> directMessages = new ArrayList<>();
    private List<GroupMessagePush> groupMessages = new ArrayList<>();

    public PendingSyncResponse() {}

    public PendingSyncResponse(List<DirectMessagePush> directMessages, List<GroupMessagePush> groupMessages) {
        this.directMessages = directMessages != null ? directMessages : new ArrayList<>();
        this.groupMessages = groupMessages != null ? groupMessages : new ArrayList<>();
    }

    public List<DirectMessagePush> getDirectMessages() {
        return directMessages;
    }

    public void setDirectMessages(List<DirectMessagePush> directMessages) {
        this.directMessages = directMessages;
    }

    public List<GroupMessagePush> getGroupMessages() {
        return groupMessages;
    }

    public void setGroupMessages(List<GroupMessagePush> groupMessages) {
        this.groupMessages = groupMessages;
    }
}
