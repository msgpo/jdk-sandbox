/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.jfr.consumer;

import java.io.IOException;
import java.nio.file.Path;
import java.security.AccessControlContext;
import java.util.Arrays;
import java.util.Objects;

import jdk.jfr.internal.consumer.FileAccess;
import jdk.jfr.internal.consumer.RecordingInput;

/**
 * Implementation of an event stream that operates against a recording file.
 *
 */
final class EventFileStream extends AbstractEventStream {
    private final RecordingInput input;
    private ChunkParser chunkParser;
    private RecordedEvent[] sortedList;

    public EventFileStream(AccessControlContext acc, Path path) throws IOException {
        super(acc, false);
        Objects.requireNonNull(path);
        this.input = new RecordingInput(path.toFile(), FileAccess.UNPRIVILIGED);
    }

    @Override
    public void start() {
        start(0);
    }

    @Override
    public void startAsync() {
        startAsync(0);
    }

    @Override
    public void close() {
        setClosed(true);
        runCloseActions();
        try {
            input.close();
        } catch (IOException e) {
            // ignore
        }
    }

    @Override
    public void process() throws IOException {
        StreamConfiguration c = configuration;
        long start = 0;
        long end = Long.MAX_VALUE;
        if (c.getStartTime() != null) {
            start = c.getStartNanos();
        }
        if (c.getEndTime() != null) {
            end = c.getEndNanos();
        }

        chunkParser = new ChunkParser(input, c.getReuse());
        while (!isClosed()) {
            if (chunkParser.getStartNanos() > end) {
                close();
                return;
            }
            c = configuration;
            boolean ordered = c.getOrdered();
            chunkParser.setFlushOperation(getFlushOperation());
            chunkParser.setFilterStart(start);
            chunkParser.setFilterEnd(end);
            chunkParser.setReuse(c.getReuse());
            chunkParser.setOrdered(ordered);
            chunkParser.resetEventCache();
            chunkParser.setParserFilter(c.getFiler());
            chunkParser.updateEventParsers();
            clearLastDispatch();
            if (ordered) {
                processOrdered();
            } else {
                processUnordered();
            }
            if (chunkParser.isLastChunk()) {
                return;
            }
            chunkParser = chunkParser.nextChunkParser();
        }
    }

    private void processOrdered() throws IOException {
        if (sortedList == null) {
            sortedList = new RecordedEvent[10_000];
        }
        RecordedEvent event;
        int index = 0;
        while (true) {
            event = chunkParser.readEvent();
            if (event == null) {
                Arrays.sort(sortedList, 0, index, END_TIME);
                for (int i = 0; i < index; i++) {
                    dispatch(sortedList[i]);
                }
                return;
            }
            if (index == sortedList.length) {
                RecordedEvent[] tmp = sortedList;
                sortedList = new RecordedEvent[2 * tmp.length];
                System.arraycopy(tmp, 0, sortedList, 0, tmp.length);
            }
            sortedList[index++] = event;
        }
    }

    private void processUnordered() throws IOException {
        while (!isClosed()) {
            RecordedEvent event = chunkParser.readEvent();
            if (event == null) {
                return;
            }
            dispatch(event);
        }
    }
}
