/*******************************************************************************
 * Copyright (c) 2022 GODYO Business Solutions AG and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Peter Hermsdorf, GODYO Business Solutions AG - initial API and implementation
 ******************************************************************************/

package org.eclipse.ecf.provider.tcpsocket.examples.service.provider;

public class UserManager {

	private static final ThreadLocal<String> currentUser = new ThreadLocal<>();

	private UserManager() {
	}

	public static String getUser() {
		return currentUser.get();
	}

	public static void setUser(String username) {
		if (username == null) {
			currentUser.remove();
		} else {
			currentUser.set(username);
		}
	}

}
