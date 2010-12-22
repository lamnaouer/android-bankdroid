/*
 * Copyright (C) 2010 Nullbyte <http://nullbyte.eu>
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

package com.liato.bankdroid.banks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.text.Html;
import android.text.InputType;
import android.util.Log;

import com.liato.bankdroid.Account;
import com.liato.bankdroid.Bank;
import com.liato.bankdroid.BankException;
import com.liato.bankdroid.Helpers;
import com.liato.bankdroid.LoginException;
import com.liato.bankdroid.R;
import com.liato.bankdroid.Transaction;
import com.liato.urllib.Urllib;

public class IkanoBank extends Bank {
    private static final String TAG = "IkanoBank";
    private static final String NAME = "Ikano Bank";
    private static final String NAME_SHORT = "ikanobank";
    private static final String URL = "https://secure.ikanobank.se/engines/page.aspx?structid=1895";
    private static final int BANKTYPE_ID = Bank.IKANOBANK;
    private static final int INPUT_TYPE_USERNAME = InputType.TYPE_CLASS_PHONE;
    private static final int INPUT_TYPE_PASSWORD = InputType.TYPE_CLASS_PHONE;
    private static final String INPUT_HINT_USERNAME = "ÅÅMMDDXXXX";

    private Pattern reEventValidation = Pattern.compile("__EVENTVALIDATION\"\\s+.*?value=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private Pattern reViewState = Pattern.compile("__VIEWSTATE\"\\s+.*?value=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private Pattern reAccounts = Pattern.compile("(ctl\\d{1,}_rptAccountList_ctl\\d{1,}_RowLink)[^>]+>([^<]+)</a>\\s*</td>\\s*<td>([^<]+)</td>\\s*<td>[^<]+</td>\\s*<td[^>]+>([^<]+)</td>", Pattern.CASE_INSENSITIVE);
    private Pattern reTransactions = Pattern.compile("<td>(\\d{4}-\\d{2}-\\d{2})</td>\\s*<td>([^<]+)</td>\\s*<td>[^<]+</td>\\s*<td[^>]+>([^<]+)</td>", Pattern.CASE_INSENSITIVE);
    private String response = null;

    public IkanoBank(Context context) {
        super(context);
        super.TAG = TAG;
        super.NAME = NAME;
        super.NAME_SHORT = NAME_SHORT;
        super.BANKTYPE_ID = BANKTYPE_ID;
        super.URL = URL;
        super.INPUT_TYPE_USERNAME = INPUT_TYPE_USERNAME;
        super.INPUT_TYPE_PASSWORD = INPUT_TYPE_PASSWORD;
        super.INPUT_HINT_USERNAME = INPUT_HINT_USERNAME;
    }

    public IkanoBank(String username, String password, Context context) throws BankException, LoginException {
        this(context);
        this.update(username, password);
    }


    public Urllib login() throws LoginException, BankException {
        urlopen = new Urllib(true);
        String response = null;
        Matcher matcher;
        try {
            response = urlopen.open("https://secure.ikanobank.se/login");
            matcher = reViewState.matcher(response);
            if (!matcher.find()) {
                throw new BankException(res.getText(R.string.unable_to_find).toString()+" ViewState.");
            }
            String strViewState = matcher.group(1);
            matcher = reEventValidation.matcher(response);
            if (!matcher.find()) {
                throw new BankException(res.getText(R.string.unable_to_find).toString()+" EventValidation.");
            }
            String strEventValidation = matcher.group(1);

            List <NameValuePair> postData = new ArrayList <NameValuePair>();
            postData.add(new BasicNameValuePair("__LASTFOCUS", ""));
            postData.add(new BasicNameValuePair("__EVENTTARGET", "ctl02$lbLogin"));
            postData.add(new BasicNameValuePair("__EVENTARGUMENT", ""));
            postData.add(new BasicNameValuePair("__VIEWSTATE", strViewState));
            postData.add(new BasicNameValuePair("ctl02$txtSocialSecurityNumber", username));
            postData.add(new BasicNameValuePair("ctl02$txtPinCode", password));
            postData.add(new BasicNameValuePair("__EVENTVALIDATION", strEventValidation));
            response = urlopen.open("https://secure.ikanobank.se/engines/page.aspx?structid=1895", postData);

            if (response.contains("Ogiltigt personnummer eller")) {
                throw new LoginException(res.getText(R.string.invalid_username_password).toString());
            }
        }
        catch (ClientProtocolException e) {
            throw new BankException(e.getMessage());
        }
        catch (IOException e) {
            throw new BankException(e.getMessage());
        }
        return urlopen;
    }

    @Override
    public void update() throws BankException, LoginException {
        super.update();
        if (username == null || password == null || username.length() == 0 || password.length() == 0) {
            throw new LoginException(res.getText(R.string.invalid_username_password).toString());
        }

        urlopen = login();
        Matcher matcher = reAccounts.matcher(response);
        while (matcher.find()) {
            /*
             * Capture groups:
             * GROUP                    EXAMPLE DATA
             * 1: ID                    ctl07_rptAccountList_ctl00_RowLink
             * 2: Name                  Kontonamn1
             * 3: Account number        123456
             * 4: Balance               316 000,39
             * 
             */    
            accounts.add(new Account(Html.fromHtml(matcher.group(2)).toString().trim(), Helpers.parseBalance(matcher.group(4).trim()), matcher.group(1).trim()));
            balance = balance.add(Helpers.parseBalance(matcher.group(4)));
        }

        if (accounts.isEmpty()) {
            throw new BankException(res.getText(R.string.no_accounts_found).toString());
        }
        super.updateComplete();
    }

    @Override
    public void updateTransactions(Account account, Urllib urlopen) throws LoginException, BankException {
        super.updateTransactions(account, urlopen);

        // Find viewstate and eventvalidation from last page.
        Matcher matcher;
        matcher = reViewState.matcher(response);
        if (!matcher.find()) {
            Log.d(TAG, "Unable to find ViewState. L156.");
        }
        String strViewState = matcher.group(1);
        matcher = reEventValidation.matcher(response);
        if (!matcher.find()) {
            Log.d(TAG, "Unable to find EventValidation. L161.");
        }
        String strEventValidation = matcher.group(1);       

        try {
            List <NameValuePair> postData = new ArrayList <NameValuePair>();
            postData.add(new BasicNameValuePair("__EVENTTARGET", account.getId().replace("_", "$")));
            postData.add(new BasicNameValuePair("__EVENTARGUMENT", ""));            
            postData.add(new BasicNameValuePair("__VIEWSTATE", strViewState));            
            postData.add(new BasicNameValuePair("__EVENTVALIDATION", strEventValidation));            
            response = urlopen.open("https://secure.ikanobank.se/engines/page.aspx?structid=1787", postData);

            matcher = reTransactions.matcher(response);
            ArrayList<Transaction> transactions = new ArrayList<Transaction>();
            while (matcher.find()) {
                /*
                 * Capture groups:
                 * GROUP                EXAMPLE DATA
                 * 1: Date              2010-10-27
                 * 2: Specification     ÍVERFÍRING
                 * 3: Amount            50
                 *   
                 */                    
                transactions.add(new Transaction(matcher.group(1).trim(),
                        Html.fromHtml(matcher.group(2)).toString().trim(),
                        Helpers.parseBalance(matcher.group(3))));
            }
            account.setTransactions(transactions);

        } catch (ClientProtocolException e) {
            Log.e(TAG, e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
        finally {
            super.updateComplete();
        }
    }       	
}