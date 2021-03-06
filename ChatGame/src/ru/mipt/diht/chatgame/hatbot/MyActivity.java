package ru.mipt.diht.chatgame.hatbot;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import java.io.*;
import java.net.URI;
import java.util.*;

public class MyActivity extends Activity implements OnInitListener {

    protected static final int REQUEST_OK = 1;

    private TextToSpeech textToSpeech;
    private String host = "http://hatbot.me/api";
    private String correctWord = "";
    private int constMaximumExplanations = 5;
    private String randomWord = "/random_word";
    private JSONArray savedChat;
    private String clientAppStr = "android_hatbot";
    private int currentScore;
    private String saveFile = "savefile.txt";
    private int currentExplanation;
    private List<String> explanationList;
    private InputMethodManager keyboard;



    protected void makeInvisible(int id)
    {
        findViewById(id).setVisibility(View.INVISIBLE);
    }

    protected void makeVisible(int id)
    {
        findViewById(id).setVisibility(View.VISIBLE);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        savedChat = new JSONArray();
        textToSpeech = new TextToSpeech(this, this);
        explanationList = new ArrayList<String>();
        makeInvisible(R.id.scoreTextView);

    }

    @Override
    public void onInit(int code) {
        final Context context = this;
        if (code == TextToSpeech.SUCCESS) {
            textToSpeech.setLanguage(new Locale("ru"));
        } else {
            textToSpeech = null;
            showToast("Text to speech failed", context);
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    private String getBestScore()
    {
        final Context context = this;
        try
        {
            File file = new File(context.getFilesDir(), saveFile);
            if (!file.exists()) {
                if (!file.createNewFile())
                    return "0";
            }
            InputStream inputstream = openFileInput(saveFile);
            if (inputstream != null) {
                InputStreamReader isr = new InputStreamReader(inputstream);
                BufferedReader reader = new BufferedReader(isr);
                String str = reader.readLine();
                inputstream.close();
                if (str != null) {
                    return str;
                } else {
                    return "0";
                }
            }
        } catch (Throwable t) {
            showToast("Exception: " + t.toString(), context);
        }
        return "0";
    }

    private void updateScore() {
        TextView scoreTextView = (TextView) findViewById(R.id.scoreTextView);
        scoreTextView.setVisibility(View.VISIBLE);
        String bestScore = getBestScore();
        if (Integer.parseInt(bestScore) < currentScore) {
            scoreTextView.setText("Счет: " + String.valueOf(currentScore) + " (новый рекорд!)");
        } else {
            scoreTextView.setText("Счет: " + String.valueOf(currentScore) + " Рекорд: " + bestScore);
        }
    }

    public void startGame(View v) {
        if (internetCheck()) {
            try
            {
                TitleTask titleTask = new TitleTask();
                Log.d("startGame", "startGame: before execute");
                String titleTaskExecuteResult = titleTask.execute(host + randomWord).get();
                titleTaskOnPostExecute(titleTaskExecuteResult);
                Log.d("startGame", "startGame: after execute explanationList size = " + explanationList.size());
                Log.d("startGame", "startGame: after execute result = " + titleTaskExecuteResult);
                //titleTask.get();
            } catch (Throwable t) {
                showToast("Exception startGame: " + t.toString(), this.getApplication());
            }
            if (explanationList.size() == 0) {
                showToast("Сервер недоступен", this.getApplication());
            } else {

                makeVisible(R.id.inGameLayout);
                makeInvisible(R.id.startLayout);
                makeVisible(R.id.scoreTextView);
                currentScore = 0;
                updateScore();
                savedChat = new JSONArray();
                giveNextExplanationToUser(false);
            }
        }
    }

    public void restartGame(View v)
    {
        if (internetCheck()) {
            makeInvisible(R.id.loseGameLayout);
            startGame(v);
        }
    }

    public void sendUserAnswer(View v) {
        shutSpeaker();

        if (internetCheck()) {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, new Locale("ru"));
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, new Locale("ru"));
            i.putExtra(RecognizerIntent.EXTRA_PROMPT,
                    "назовите слово");
            final Context context = this;
            try {
                startActivityForResult(i, REQUEST_OK);
            } catch (Exception e) {
                showToast("Ошибка встроенного распознавателя речи", context);
            }
        }
    }

    public void sayCurrentExplanation(View v)
    {
        String explanation = explanationList.get(currentExplanation).replace("*", "");
        sayText(explanation);
    }

    private void titleTaskExecute()
    {
        try
        {
            String result = (new TitleTask()).execute(host + randomWord).get();
            titleTaskOnPostExecute(result);
        }
        catch (Throwable t) {
            showToast("Exception startGame: " + t.toString(), this.getApplication());
        }
    }

    private void definitionTaskExecute(String result)
    {
        try
        {
            DefinitionTask definitionTask = new DefinitionTask();
            String executeResult = definitionTask.execute(host + "/explain_list?word=" + result).get();
            definitionTaskOnPostExecute(executeResult);
        }
        catch (Throwable t) {
            Log.wtf("TitleTask", "exception in TitleTask.onPostExecute: " + t);
        }
    }

    private void shutSpeaker()
    {
        if (textToSpeech.isSpeaking())
            textToSpeech.stop();
    }

    public void onClickKeyboardButton(View view)
    {
        //shutSpeaker();
        final EditText answerEditText = (EditText)findViewById(R.id.answerEditText);
        makeVisible(R.id.answerEditText);
        keyboard = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.showSoftInput(answerEditText, InputMethodManager.SHOW_IMPLICIT);
        answerEditText.setOnEditorActionListener(
                new EditText.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                                actionId == EditorInfo.IME_ACTION_DONE ||
                                event.getAction() == KeyEvent.ACTION_DOWN &&
                                        event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                            String answerText = answerEditText.getText().toString();
                            if (answerText.length() != 0)
                            {
                                Log.d("debug", "onCLickKeyboardButton in edit text " + answerText);
                                answerProcessing(answerText);
                                closeTextInput();
                            }
                            else closeTextInput();
                            return true;
                        }
                        return false;
                    }
                });
    }

    public void continueGame(View v)     {
        if (internetCheck()) {
            makeVisible(R.id.inGameLayout);
            makeInvisible(R.id.continueGameLayout);
            updateScore();
            titleTaskExecute();
            savedChat = new JSONArray();
            giveNextExplanationToUser(false);
        }
    }

    private void addChatEntry(int msecs, String actor, String text)
    {
        JSONObject json = new JSONObject();
        try
        {
            json.put("msecs_after_start", msecs);
            json.put("actor", actor);
            byte btext[] = text.getBytes("UTF-8");
            json.put("text", new String(btext, "UTF-8"));
            savedChat.put(json);
        }
        catch (Exception e)
        {
            Log.wtf("exceptions", "exception in getChatEntry");
            Log.d("getChatEntry", "exception in getChatEntry");
        }
    }

    private void sayText(String text) {
        addChatEntry(0, "app", text);
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    private void closeTextInput()
    {
        EditText answerEditText = (EditText)findViewById(R.id.answerEditText);
        if (keyboard != null)
            keyboard.hideSoftInputFromWindow(answerEditText.getWindowToken(), 0);
        answerEditText.setText("");
        makeInvisible(R.id.answerEditText);
    }

    public void onNextExplanationButtonClick(View v)
    {
        closeTextInput();
        giveNextExplanationToUser(false);
    }

    private boolean giveNextExplanationToUser(boolean flagAfterWrongAnswer) {
        if (flagAfterWrongAnswer)
            sayText("Неверный ответ.");
        currentExplanation++;
        if (outOfExplanations())
        {
            if (currentExplanation == 0)
            {
                Log.e("giveFirstExplanationToUser", "Error: there are NO explanations!");
                Log.d("giveFirstExplanationToUser", "Error: there are NO explanations!");
            }
            finishGame();
            return false;
        }
        else
        {
            if (flagAfterWrongAnswer)
            {
                sayText("Прослушайте другое объяснение.");
            }
            String explanation = explanationList.get(currentExplanation).replace("*", "");
            sayText(explanation);
            return true;
        }
    }

    private int getEditDist(String userAnswer, String correctWord)
    {
        int dp[][] = new int[userAnswer.length()][correctWord.length()];
        for (int i = 0; i < userAnswer.length(); i++)
            for (int j = 0; j < correctWord.length(); j++)
            {
                if (userAnswer.charAt(i) == correctWord.charAt(j))
                {
                    if (i == 0)
                        dp[i][j] = j;
                    else if (j == 0)
                        dp[i][j] = i;
                    else dp[i][j] = dp[i - 1][j - 1];
                }
                else
                {
                    int changeI = (i - 1 >= 0 ? dp[i - 1][j] + 1 : j + 2);
                    int changeJ = (j - 1 >= 0 ? dp[i][j - 1] + 1 : i + 2);
                    dp[i][j] = Math.min(changeI, changeJ);
                }
            }
        return dp[userAnswer.length() - 1][correctWord.length() - 1];
    }

    private boolean checkCorrect(String userAnswer, String correctWord)
    {
        int editDist = getEditDist(userAnswer, correctWord);
        Log.d("checkCorrect", "userAnswer = " + userAnswer + " correctWord = " + correctWord + " editDist = " + editDist);
        return (editDist <= Math.min(userAnswer.length(), correctWord.length()) / 3);
    }

    private void sendWordLog()
    {
        new RetrieveFeedTask().execute();
    }

    class RetrieveFeedTask extends AsyncTask<String, Void, Void> {

        protected Void doInBackground(String... urls) {
            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(host + "/chat_log");
            try {
                JSONObject holder = new JSONObject();
                holder.put("entries", savedChat);
                holder.put("word", correctWord);
                holder.put("client_app", clientAppStr);
                StringEntity stringEntity = new StringEntity(holder.toString(), "UTF-8");
                post.addHeader("content-type", "application/json");
                post.addHeader("charset", "utf-8");
                post.addHeader("accept-charset", "utf-8");
                post.setEntity(stringEntity);
                HttpResponse response = client.execute(post);
                final String responseEntity = EntityUtils.toString(response.getEntity());
                Log.d("sendExplanationLog", "response after post execute:" + responseEntity);
            } catch (Exception e) {
                Log.wtf("exceptions", "exception in sendWordLog" + e);
                Log.d("sendWordLog", "exception in sendWordLog" + e);
            }
            return null;
        }
    }

    private void finishGame()
    {
        showToast("Загаданное слово: " + correctWord, this);
        sendWordLog();
        makeVisible(R.id.loseGameLayout);
        makeInvisible(R.id.inGameLayout);
        TextView scoreTextView = (TextView)findViewById(R.id.scoreTextView);
        scoreTextView.setText("Итоговый результат: " + String.valueOf(currentScore));
        String bestScore = getBestScore();
        if (Integer.parseInt(bestScore) < currentScore) {
            sayText("Новый рекорд! Загаданное слово - " + correctWord);
            try {
                String toWrite = String.valueOf(currentScore);
                FileOutputStream fos = openFileOutput(saveFile, Context.MODE_PRIVATE);
                fos.write(toWrite.getBytes());
                fos.close();
            } catch (Throwable t) {
                showToast("Exception: " + t.toString(), this);
            }
        } else {
            sayText("Игра окончена. Загаданное слово - " + correctWord);
        }
    }

    private boolean outOfExplanations()
    {
        return (currentExplanation >= explanationList.size() || currentExplanation >= constMaximumExplanations);
    }

    private void answerProcessing(String userAnswer)
    {
        Log.d("debug", "answerProcessing userAnswer = " + userAnswer);
        addChatEntry(0, "user", userAnswer);
        if (checkCorrect(userAnswer, correctWord)) {
            showToast("Вы ответили верно: " + correctWord, this);
            Log.d("debug", "answerProcessing correct!");
            sayText("Вы угадали!");
            sendWordLog();
            makeVisible(R.id.continueGameLayout);
            makeInvisible(R.id.inGameLayout);
            currentScore++;
            updateScore();
        } else {
            showToast("Неверный ответ: " + userAnswer, this);
            Log.d("debug", "answerProcessing wrong!");
            giveNextExplanationToUser(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final Context context = this;
        if (requestCode==REQUEST_OK && resultCode==RESULT_OK) {
            ArrayList<String> userAnswers = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String userAnswer = userAnswers.get(0);
            Log.d("onActivityResult", "userAnswer = " + userAnswer);
            answerProcessing(userAnswer);
        }
    }

    private void titleTaskOnPostExecute(String result) {

        Log.d("TitleTask", "onPostExecute result = " + result);
        correctWord = result;
        definitionTaskExecute(result);
    }

    private void definitionTaskOnPostExecute(String result) {
        try {
            explanationList.clear();
            List<String> jsons = new ArrayList<String>();
            int l = 0, r = 0;
            for (int i = 0; i < result.length(); i++) {
                if (result.charAt(i) == '{') {
                    l = i;
                }
                if (result.charAt(i) == '}') {
                    r = i;
                    jsons.add(result.substring(l, r + 1));
                }
            }
            for (String json : jsons) {
                explanationList.add((new JSONObject(json)).getString("text"));
            }
            currentExplanation = -1;
            Log.d("DefinitionTask", "onPostExecute explanationList size = " + explanationList.size());
            for (int j = 0; j < explanationList.size(); j++)
                Log.d("DefinitionTask", "explanationList[" + j + "] = " + explanationList.get(j));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private class TitleTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... params) {
            try {
                HttpClient httpclient = new DefaultHttpClient();

                HttpGet request = new HttpGet();
                URI website = new URI(params[0]);
                request.setURI(website);
                HttpResponse response = httpclient.execute(request);
                Log.d("TitleTask", "before forming retValue");
                String retValue = new BufferedReader(new InputStreamReader(
                        response.getEntity().getContent())).readLine();
                Log.d("TitleTask", "after forming retValue = " + retValue);
                return retValue;
            } catch (Exception e) {
                Log.wtf("exception in TitleTask.doInBackground", e);
            }
            return null;
        }
    }

    private class DefinitionTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... params) {
            try {
                HttpClient httpclient = new DefaultHttpClient();

                HttpGet request = new HttpGet();
                URI website = new URI(params[0]);
                request.setURI(website);
                HttpResponse response = httpclient.execute(request);
                Log.d("DefinitionTask", "response = " + response.toString());
                String retValue = new BufferedReader(new InputStreamReader(
                        response.getEntity().getContent())).readLine();
                //Log.d("DefinitionTask", "retValue = " + retValue);
                return retValue;
            } catch (Exception e) {
                Log.e("DefinitionTask", "doInBackground exception" + e.toString());
            }
            return null;
        }
    }

    public void showToast(final String toast, final Context context) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                Toast.makeText(context, toast, Toast.LENGTH_SHORT).show();
            }
        });
    }


    private boolean internetCheck() {
        ConnectionDetector connectionDetector = new ConnectionDetector(getApplicationContext());
        final Context context = this;
        if (connectionDetector.isConnectingToInternet()) {
            return true;
        } else {
            showToast("Нет подключения к интернету!", context);
            return false;
        }
    }

    public class ConnectionDetector {

        private Context _context;

        public ConnectionDetector(Context context) {
            this._context = context;
        }

        public boolean isConnectingToInternet() {
            ConnectivityManager connectivity = (ConnectivityManager) _context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity != null) {
                NetworkInfo[] info = connectivity.getAllNetworkInfo();
                if (info != null)
                    for (NetworkInfo anInfo : info)
                        if (anInfo.getState() == NetworkInfo.State.CONNECTED) {
                            return true;
                        }

            }
            return false;
        }
    }
}
