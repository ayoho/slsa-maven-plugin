/*
 * Copyright 2023 International Business Machines Corp..
 * 
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. Licensed under the Apache License, 
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package io.slsa.maven.plugin.exceptions;

public class SlsaPredicateGenerationException extends Exception {

    private static final String ERROR_MSG = "An error occurred while generating the SLSA predicate: %s";

    private final String errorMsg;

    public SlsaPredicateGenerationException(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public SlsaPredicateGenerationException(String errorMsg, Throwable t) {
        super(t);
        this.errorMsg = errorMsg;
    }

    @Override
    public String getMessage() {
        return String.format(ERROR_MSG, errorMsg);
    }

}
