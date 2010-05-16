/*
 * Copyright (C) 2010 Brad Fitzpatrick <brad@danga.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.danga.garagedoor;

public final class Preferences {
    public static final String NAME = "GarageOpener";
	
    // e.g. "http://yourhouse.dyndys.com:1234/open-garage-script.cgi"
    public static final String KEY_URL = "garagedoor.url";
    
    // the shared secret (~password) with your server.
    public static final String KEY_SECRET = "garagedoor.secret";

    // SSID to auto-open the garage on
    public static final String KEY_SSID = "garagedoor.ssid";
    
    private Preferences() {}
}
