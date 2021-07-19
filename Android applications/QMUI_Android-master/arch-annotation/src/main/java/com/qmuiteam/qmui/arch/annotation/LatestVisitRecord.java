/*
 * Tencent is pleased to support the open source community by making QMUI_Android available.
 *
 * Copyright (C) 2017-2018 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the MIT License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/MIT
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.qmuiteam.qmui.arch.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * This annotation can be used when you want to revert to last Fragment(Activity) that
 * was visited before the app exited.
 * <p>
 * if annotated for subclass of QMUIFragment, such as FragmentA, it must be annotated
 * in the subclass of QMUIFragmentActivity, such as FragmentActivityA. FragmentActivityA
 * must be annotated by FirstFragments or DefaultFirstFragment and the value must contain
 * FragmentA.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LatestVisitRecord {
    boolean onlyForDebug() default false;
}
