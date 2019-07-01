/*
 * Copyright (C) by MinterTeam. 2018
 * @link <a href="https://github.com/MinterTeam">Org Github</a>
 * @link <a href="https://github.com/edwardstock">Maintainer Github</a>
 *
 * The MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package network.minter.bipwallet.settings;

import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.arellomobile.mvp.MvpView;
import com.arellomobile.mvp.viewstate.strategy.OneExecutionStateStrategy;
import com.arellomobile.mvp.viewstate.strategy.StateStrategyType;

import network.minter.bipwallet.internal.dialogs.WalletDialog;
import network.minter.bipwallet.internal.helpers.forms.InputGroup;
import network.minter.bipwallet.security.SecurityModule;

/**
 * minter-android-wallet. 2018
 *
 * @author Eduard Maximovich <edward.vstock@gmail.com>
 */
public interface SettingsTabModule {
    interface PasswordChangeMigrationView extends MvpView {
        void setTextChangedListener(InputGroup.OnTextChangedListener listener);
        void setFormValidateListener(InputGroup.OnFormValidateListener listener);
        void setOnClickSubmit(View.OnClickListener listener);
        @StateStrategyType(OneExecutionStateStrategy.class)
        void startDialog(WalletDialog.DialogExecutor executor);
        void setEnableSubmit(boolean enable);
        void finish();
    }

    interface SettingsTabView extends MvpView {
        void setOnFreeCoinsClickListener(View.OnClickListener listener);
        void showFreeCoinsButton(boolean show);
        @StateStrategyType(OneExecutionStateStrategy.class)
        void startLogin();
        void setMainAdapter(RecyclerView.Adapter<?> mainAdapter);
        void setAdditionalAdapter(RecyclerView.Adapter<?> additionalAdapter);
        void setSecurityAdapter(RecyclerView.Adapter<?> securityAdapter);
        @StateStrategyType(OneExecutionStateStrategy.class)
        void startAddressList();
        @StateStrategyType(OneExecutionStateStrategy.class)
        void startAvatarChooser();
        @StateStrategyType(OneExecutionStateStrategy.class)
        void startPasswordChange();
        void showMessage(CharSequence message);
        @StateStrategyType(OneExecutionStateStrategy.class)
        void startDialog(WalletDialog.DialogExecutor executor);
        @StateStrategyType(OneExecutionStateStrategy.class)
        void startPinCodeManager(int requestCode, SecurityModule.PinMode mode);
    }
}
