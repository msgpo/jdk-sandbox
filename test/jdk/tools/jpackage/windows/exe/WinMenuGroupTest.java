/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * Test --win-menu and --win-menu-group parameters.
 * Output of the test should be WinMenuGroupTest-1.0.exe installer.
 * The output installer should provide the same functionality as the default 
 * installer (see description of the default installer in Test.java) plus 
 * it should create a shortcut for application launcher in Windows Menu in 
 * "C:\ProgramData\Microsoft\Windows\Start Menu\Programs\WinMenuGroupTest_MenuGroup" folder.
 */

/*
 * @test
 * @summary jpackage create installer test
 * @library ../../helpers
 * @library ../base
 * @build JPackageHelper
 * @build JPackagePath
 * @build JPackageInstallerHelper
 * @build WinMenuGroupBase
 * @requires (os.family == "windows")
 * @modules jdk.jpackage
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @run main/othervm -Xmx512m WinMenuGroupTest
 */
public class WinMenuGroupTest {
    private static final String TEST_NAME = "WinMenuGroupTest";
    private static final String EXT = "exe";

    public static void main(String[] args) throws Exception {
        if (jdk.jpackage.internal.WinMsiBundler.isSupported()) {
            WinMenuGroupBase.run(TEST_NAME, EXT);
        }
    }
}
