/*
 * Copyright (c) 2016 Lucko (Luck) <luck@lucko.me>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.utils;

import com.google.common.collect.ImmutableMap;
import lombok.*;
import me.lucko.luckperms.constants.Patterns;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * An immutable permission node
 */
@SuppressWarnings({"WeakerAccess", "unused"})
@ToString
@EqualsAndHashCode
public class Node implements me.lucko.luckperms.api.Node {
    public static me.lucko.luckperms.api.Node fromSerialisedNode(String s, Boolean b) {
        return builderFromSerialisedNode(s, b).build();
    }

    public static me.lucko.luckperms.api.Node.Builder builderFromSerialisedNode(String s, Boolean b) {
        if (s.contains("/")) {
            String[] parts = Patterns.SERVER_DELIMITER.split(s, 2);
            // 0=server(+world)   1=node

            // WORLD SPECIFIC
            if (parts[0].contains("-")) {
                String[] serverParts = Patterns.WORLD_DELIMITER.split(parts[0], 2);
                // 0=server   1=world

                if (parts[1].contains("$")) {
                    String[] tempParts = Patterns.TEMP_DELIMITER.split(parts[1], 2);
                    return new Node.Builder(tempParts[0], true).setServerRaw(serverParts[0]).setWorld(serverParts[1])
                            .setExpiry(Long.parseLong(tempParts[1])).setValue(b);
                } else {
                    return new Node.Builder(parts[1], true).setServerRaw(serverParts[0]).setWorld(serverParts[1]).setValue(b);
                }

            } else {
                // SERVER BUT NOT WORLD SPECIFIC
                if (parts[1].contains("$")) {
                    String[] tempParts = Patterns.TEMP_DELIMITER.split(parts[1], 2);
                    return new Node.Builder(tempParts[0], true).setServerRaw(parts[0]).setExpiry(Long.parseLong(tempParts[1])).setValue(b);
                } else {
                    return new Node.Builder(parts[1], true).setServerRaw(parts[0]).setValue(b);
                }
            }
        } else {
            // NOT SERVER SPECIFIC
            if (s.contains("$")) {
                String[] tempParts = Patterns.TEMP_DELIMITER.split(s, 2);
                return new Node.Builder(tempParts[0], true).setExpiry(Long.parseLong(tempParts[1])).setValue(b);
            } else {
                return new Node.Builder(s, true).setValue(b);
            }
        }
    }

    @Getter
    private final String permission;

    @Getter
    private Boolean value;

    private String server = null;
    private String world = null;

    private long expireAt = 0L;

    private final Map<String, String> extraContexts = new HashMap<>();

    /**
     * Make an immutable node instance
     * @param permission the actual permission node
     * @param value the value (if it's *not* negated)
     * @param expireAt the time when the node will expire
     * @param server the server this node applies on
     * @param world the world this node applies on
     * @param extraContexts any additional contexts applying to this node
     */
    public Node(String permission, boolean value, long expireAt, String server, String world, Map<String, String> extraContexts) {
        if (permission == null || permission.equals("")) {
            throw new IllegalArgumentException("Empty permission");
        }

        if (server != null && (server.equalsIgnoreCase("global") || server.equals(""))) {
            server = null;
        }

        if (world != null && world.equals("")) {
            world = null;
        }

        if (world != null && server == null) {
            server = "global";
        }

        this.permission = permission;
        this.value = value;
        this.expireAt = expireAt;
        this.server = server;
        this.world = world;

        if (extraContexts != null) {
            this.extraContexts.putAll(extraContexts);
        }
    }

    public boolean isNegated() {
        return !value;
    }

    public Optional<String> getServer() {
        return Optional.ofNullable(server);
    }

    public Optional<String> getWorld() {
        return Optional.ofNullable(world);
    }

    public boolean isServerSpecific() {
        return getServer().isPresent();
    }

    public boolean isWorldSpecific() {
        return getWorld().isPresent();
    }

    @Override
    public boolean shouldApplyOnServer(String server, boolean includeGlobal, boolean applyRegex) {
        if (server == null || server.equals("")) {
            return true;
        }

        if (isServerSpecific()) {
            if (server.toLowerCase().startsWith("r=") && applyRegex) {
                Pattern p = Patterns.compile(server.substring(2));
                if (p == null) {
                    return false;
                }
                return p.matcher(this.server).matches();
            }

            if (server.startsWith("(") && server.endsWith(")") && server.contains("|")) {
                final String bits = server.substring(1, server.length() - 1);
                String[] parts = Patterns.VERTICAL_BAR.split(bits);

                for (String s : parts) {
                    if (s.equalsIgnoreCase(this.server)) {
                        return true;
                    }
                }

                return false;
            }

            return this.server.equalsIgnoreCase(server);
        } else {
            return includeGlobal;
        }
    }

    @Override
    public boolean shouldApplyOnWorld(String world, boolean includeGlobal, boolean applyRegex) {
        if (world == null || world.equals("")) {
            return true;
        }

        if (isWorldSpecific()) {
            if (world.toLowerCase().startsWith("r=") && applyRegex) {
                Pattern p = Patterns.compile(world.substring(2));
                if (p == null) {
                    return false;
                }
                return p.matcher(this.world).matches();
            }

            if (world.startsWith("(") && world.endsWith(")") && world.contains("|")) {
                final String bits = world.substring(1, world.length() - 1);
                String[] parts = Patterns.VERTICAL_BAR.split(bits);

                for (String s : parts) {
                    if (s.equalsIgnoreCase(this.world)) {
                        return true;
                    }
                }

                return false;
            }

            return this.world.equalsIgnoreCase(world);
        } else {
            return includeGlobal;
        }
    }

    @Override
    public boolean shouldApplyWithContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            return true;
        }

        for (Map.Entry<String, String> c : context.entrySet()) {
            if (!getExtraContexts().containsKey(c.getKey())) {
                return false;
            }

            if (!getExtraContexts().get(c.getKey()).equalsIgnoreCase(c.getValue())) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean shouldApplyOnAnyServers(List<String> servers, boolean includeGlobal) {
        for (String s : servers) {
            if (shouldApplyOnServer(s, includeGlobal, false)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean shouldApplyOnAnyWorlds(List<String> worlds, boolean includeGlobal) {
        for (String s : worlds) {
            if (shouldApplyOnWorld(s, includeGlobal, false)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public List<String> resolveWildcard(List<String> possibleNodes) {
        if (!isWildcard() || possibleNodes == null) {
            return Collections.emptyList();
        }

        String match = getPermission().substring(0, getPermission().length() - 2);
        return possibleNodes.stream().filter(pn -> pn.startsWith(match)).collect(Collectors.toList());
    }

    @Override
    public List<String> resolveShorthand() {
        if (!Patterns.SHORTHAND_NODE.matcher(getPermission()).find()) {
            return Collections.emptyList();
        }

        if (!getPermission().contains(".")) {
            return Collections.emptyList();
        }

        String[] parts = Patterns.DOT.split(getPermission());
        List<Set<String>> nodeParts = new ArrayList<>();

        for (String s : parts) {
            if ((!s.startsWith("(") || !s.endsWith(")")) || !s.contains("|")) {
                nodeParts.add(Collections.singleton(s));
                continue;
            }

            final String bits = s.substring(1, s.length() - 1);
            nodeParts.add(new HashSet<>(Arrays.asList(Patterns.VERTICAL_BAR.split(bits))));
        }

        Set<String> nodes = new HashSet<>();
        for (Set<String> set : nodeParts) {
            final Set<String> newNodes = new HashSet<>();
            if (nodes.isEmpty()) {
                newNodes.addAll(set);
            } else {
                nodes.forEach(str -> newNodes.addAll(set.stream()
                        .map(add -> str + "." + add)
                        .collect(Collectors.toList()))
                );
            }
            nodes = newNodes;
        }

        return new ArrayList<>(nodes);
    }

    public boolean isTemporary() {
        return expireAt != 0L;
    }

    public boolean isPermanent() {
        return !isTemporary();
    }

    public long getExpiryUnixTime(){
        return expireAt;
    }

    public Date getExpiry() {
        return new Date(expireAt * 1000L);
    }

    public long getSecondsTilExpiry() {
        return expireAt - (System.currentTimeMillis() / 1000L);
    }

    public boolean hasExpired() {
        return expireAt < (System.currentTimeMillis() / 1000L);
    }

    public Map<String, String> getExtraContexts() {
        return ImmutableMap.copyOf(extraContexts);
    }

    public String toSerializedNode() {
        StringBuilder builder = new StringBuilder();

        if (server != null) {
            builder.append(server);

            if (world != null) {
                builder.append("-").append(world);
            }
            builder.append("/");
        } else {
            if (world != null) {
                builder.append("global-").append(world).append("/");
            }
        }

        if (!extraContexts.isEmpty()) {
            builder.append("(");
            for (Map.Entry<String, String> entry : extraContexts.entrySet()) {
                builder.append(entry.getKey()).append("=").append(entry.getValue()).append(",");
            }

            builder.deleteCharAt(builder.length() - 1);
            builder.append(")");
        }

        builder.append(permission);

        if (expireAt != 0L) {
            builder.append("$").append(expireAt);
        }

        return builder.toString();
    }

    @Override
    public boolean isGroupNode() {
        return Patterns.GROUP_MATCH.matcher(getPermission()).matches();
    }

    @Override
    public String getGroupName() {
        if (!isGroupNode()) {
            throw new IllegalStateException("This is not a group node");
        }

        return getPermission().substring("group.".length());
    }

    @Override
    public boolean isWildcard() {
        return getPermission().endsWith(".*");
    }

    @Override
    public int getWildcardLevel() {
        return (int) getPermission().chars().filter(num -> num == Character.getNumericValue('.')).count();
    }

    @Override
    public boolean almostEquals(me.lucko.luckperms.api.Node other) {
        if (!other.getPermission().equalsIgnoreCase(this.getPermission())) {
            return false;
        }

        if (other.getServer().isPresent() != this.getServer().isPresent()) {
            if (other.getServer().isPresent()) {
                if (!other.getServer().get().equalsIgnoreCase(this.getServer().get())) {
                    return false;
                }
            }
        } else {
            return false;
        }

        if (other.getWorld().isPresent() != this.getWorld().isPresent()) {
            if (other.getWorld().isPresent()) {
                if (!other.getWorld().get().equalsIgnoreCase(this.getWorld().get())) {
                    return false;
                }
            }
        } else {
            return false;
        }

        if (!other.getExtraContexts().equals(this.getExtraContexts())) {
            return false;
        }

        if (other.isTemporary() != this.isTemporary()) {
            return false;
        }

        return true;
    }

    @Override
    public Boolean setValue(Boolean value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getKey() {
        return getPermission();
    }

    @RequiredArgsConstructor
    public static class Builder implements me.lucko.luckperms.api.Node.Builder {
        private final String permission;
        private Boolean value = true;
        private String server = null;
        private String world = null;
        private long expireAt = 0L;

        private final Map<String, String> extraContexts = new HashMap<>();

        Builder(String permission, boolean shouldConvertContexts) {
            if (!shouldConvertContexts) {
                this.permission = permission;
            } else {
                if (!Patterns.NODE_CONTEXTS.matcher(permission).matches()) {
                    this.permission = permission;
                } else {
                    String[] contextParts = permission.substring(1).split("\\)", 2);
                    // 0 = context, 1 = node
                    this.permission = contextParts[1];

                    for (String s : contextParts[0].split("\\,")) {
                        if (!s.contains("=")) {
                            // Not valid
                            continue;
                        }

                        String[] context = s.split("\\=", 2);
                        extraContexts.put(context[0], context[1]);
                    }
                }
            }
        }

        @Override
        public me.lucko.luckperms.api.Node.Builder setNegated(boolean negated) {
            value = !negated;
            return this;
        }

        @Override
        public me.lucko.luckperms.api.Node.Builder setValue(boolean value) {
            this.value = value;
            return this;
        }

        @Override
        public me.lucko.luckperms.api.Node.Builder setExpiry(long expireAt) {
            this.expireAt = expireAt;
            return this;
        }

        @Override
        public me.lucko.luckperms.api.Node.Builder setWorld(String world) {
            this.world = world;
            return this;
        }

        @Override
        public me.lucko.luckperms.api.Node.Builder setServer(String server) {
            if (server != null && ArgumentChecker.checkServer(server)) {
                throw new IllegalArgumentException("Server name invalid.");
            }

            this.server = server;
            return this;
        }

        public me.lucko.luckperms.api.Node.Builder setServerRaw(String server) {
            this.server = server;
            return this;
        }

        @Override
        public me.lucko.luckperms.api.Node.Builder withExtraContext(@NonNull String key, @NonNull String value) {
            this.extraContexts.put(key, value);
            return this;
        }

        @Override
        public me.lucko.luckperms.api.Node build() {
            return new Node(permission, value, expireAt, server, world, extraContexts);
        }
    }

}
