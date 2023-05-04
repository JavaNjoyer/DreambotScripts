import exceptions.InvalidConfigurationException;
import org.dreambot.api.Client;
import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.DropPattern;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.StandardDropPattern;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.SkillTracker;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.methods.world.World;
import org.dreambot.api.methods.world.Worlds;
import org.dreambot.api.methods.worldhopper.WorldHopper;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.listener.ChatListener;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.Player;
import org.dreambot.api.wrappers.widgets.message.Message;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.util.List;


import static org.dreambot.api.methods.tabs.Tabs.logout;

@ScriptManifest(name = "Fruit Stall Stealer", description = "Steals fruit in Hosidius (near magic trees)", author = "JavaNJoyer",
        version = 1.9, category = Category.THIEVING, image = "")
public final class FruitStallStealer extends AbstractScript implements ChatListener {
    //TODO:
    //Input for maxTime GUI -> default 1hr (+/- 15m)
    //fix player detection

    private Image image;

    private final DropPattern dp = new StandardDropPattern(0, 1, 4, 5, 8, 9, 12, 13, 16, 17, 20, 21, 24, 25, 2, 3, 6, 7, 10, 11, 14, 15, 18, 19, 22, 23, 26, 27);
    private final Area fruitStallArea = new Area(1795, 3609, 1801, 3606);
    private String s = "INITIALIZING";
    private long startTime;
    private long oldThievExp;
    private int fruitStolen;
    private long MAX_TIME = 3600000;
    private int targetLevel = Integer.MAX_VALUE;

    private int closedDialogues;

    /*
     * =========================================
     *              BOOLEANS
     * =========================================
     */
    private boolean isRunning;
    private boolean invalidInput = false;

    /*
     * =========================================
     *              ENUMS
     * =========================================
     */
    private State state;

    private enum State {
        LOGOUT, THIEVING, DROP, OUTOFPOSITION, INITIALIZING
    }

    private State getState() {
        if (!isRunning) {
            state = State.INITIALIZING;
        }
        if (isRunning && fruitStallArea.contains(player())) {
            state = State.THIEVING;
        }
        if (!fruitStallArea.contains(player())) {
            state = State.OUTOFPOSITION;
        }
        if (isRunning && Skills.getRealLevel(Skill.THIEVING) >= targetLevel || isRunning && getElapsedTime() > MAX_TIME || isRunning && invalidInput) {
            state = State.LOGOUT;
        }
        if (Inventory.isFull())
            return State.DROP;
        return state;
    }

    private Efficiency efficiencyRate;

    public enum Efficiency {
        MAX, HIGH, MEDIUM, LOW
    }

    /*
     * =========================================
     *            Start Up/Shut down
     * =========================================
     */


    public void onStart() {
        try {
            image = ImageIO.read(new URL("https://i.imgur.com/NIYDAlw.png"));
        } catch (IOException e) {
            log("Failed to load image!");
        }

        doActionsOnStart();
    }

    public void onExit() {
        doActionsOnExit();
    }

    private void doActionsOnStart() {

        createGUI();
        startTime = System.currentTimeMillis();
        SkillTracker.start(Skill.THIEVING);
        oldThievExp = Skills.getExperience(Skill.THIEVING);
        log(" ---Starting JavaNJoyer Script ---");
    }

    private void doActionsOnExit() {
        log(String.format("Gained Thieving xp: %d", (Skills.getExperience(Skill.THIEVING) - oldThievExp)));
        log("Runtime: " + getElapsedTimeAsString());
    }
    /*
     * =========================================
     *              OVERRIDES
     * =========================================
     */

    @Override
    public void onMessage(Message m) {
        if (m.getMessage().contains("You steal")) {
            fruitStolen++;
        }
    }

    @Override
    public void onPlayerMessage(Message m) {
        log(m.getMessage());
    }


    @Override
    public int onLoop() {

        switch (getState()) {
            case INITIALIZING:
                log("Awaiting user configuration...");
                sleep(randomNum(600, 1300));
                break;

            case LOGOUT:
                 if (invalidInput) {
                    log("Invalid input -- logging out.");
                } else {
                    log("Target level reached -- logging out.");
                }
                sleep(randomNum(2500, 4900));
                logout();
                stop();
                break;
            case THIEVING:
                s = "THIEVING";
                GameObject stall = GameObjects.closest("Fruit stall");
                handleDialogues();
                //nearbyPlayers();
                customSleepFunction();
                s = "All is good, thieving away";
                if (stall != null) {
                    if (stall.interactForceLeft("Steal-from")) {
                        sleep(randomNum(300, 700));
                    }
                } else log("Waiting for stall...");
                break;
            case DROP:
                s = "Inventory full - dropping all fruit";
                Inventory.setDropPattern(dp);
                if (Inventory.dropAll(i -> i != null && !i.getName().contains("Coins"))) {
                    log("Dropped all");
                }
                break;
            case OUTOFPOSITION:
                s = "OUTOFPOSITION";
                log("Out of position - please restart the script near the hosidius fruit stalls");
                log("Terminating script in 5s...");
                sleep(5000);
                stop();
                break;

        }
        return 1000;
    }

    Area westStall = new Area(1796, 3609, 1796, 3606);//verify these coords
    Area eastStall = new Area(1799, 3609, 1799, 3606);//verify these coords
//    private void nearbyPlayers(){
//        List<Player> allNearbyPlayers = Players.all((p) -> p != null && p.getID() != player().getID() && p.getTile().distance(player().getTile()) < 2);
//        if (allNearbyPlayers.size() > 0) {
//            log("Detected nearby player(s)... hopping worlds");
//            World targetWorld = Worlds.getWorld((w) -> w.isMembers() && w.isNormal());
//            if (WorldHopper.hopWorld(targetWorld)){
//                log("Hopping to " + targetWorld);
//            }
//        }
//    }

    /*
     * =========================================
     *            Helper Methods
     * =========================================
     */
    //I'm incrementing count after to prevent it from matching the same condition and sleeping twice
    //Kind of hacky but it works
    private boolean customSleepFunction() {
        switch (efficiencyRate) {
            case LOW:
                if (fruitStolen % 25 == 0 && fruitStolen > 0) {
                    log("25x fruit stolen - sleeping for 10-20s");
                    sleep(randomNum(10000, 20000));
                    fruitStolen++;
                }
                return true;
            case MEDIUM:
                if (fruitStolen % 40 == 0 && fruitStolen > 0) {
                    log("40x fruit stolen - sleeping for 10-20s");
                    sleep(randomNum(10000, 20000));
                    fruitStolen++;
                }
                return true;
            case HIGH:
                if (fruitStolen % 60 == 0 && fruitStolen > 0) {
                    log("60x fruit stolen - sleeping for 10-20s");
                    sleep(randomNum(10000, 20000));
                    fruitStolen++;
                }
                return true;
            case MAX:
                if (fruitStolen % 100 == 0 && fruitStolen > 0) {
                    log("100x fruit stolen - sleeping for 5-10s");
                    sleep(randomNum(5000, 10000));
                    fruitStolen++;
                }
                return true;
        }
        return false;
    }

    private void handleDialogues() {
        if (Dialogues.inDialogue()) {
            for (int i = 0; i < 4; i++) {
                if (Dialogues.canContinue()) {
                    if (Dialogues.continueDialogue()) {
                        sleep(randomNum(500, 750));
                    }
                } else {
                    break;
                }
            }
            closedDialogues++;
        }
    }

    private String makeTimeString(long ms) {
        final int seconds = (int) (ms / 1000) % 60;
        final int minutes = (int) ((ms / (1000 * 60)) % 60);
        final int hours = (int) ((ms / (1000 * 60 * 60)) % 24);
        final int days = (int) ((ms / (1000 * 60 * 60 * 24)) % 7);
        final int weeks = (int) (ms / (1000 * 60 * 60 * 24 * 7));
        if (weeks > 0) {
            return String.format("%02dw %03dd %02dh %02dm %02ds", weeks, days, hours, minutes, seconds);
        }
        if (weeks == 0 && days > 0) {
            return String.format("%03dd %02dh %02dm %02ds", days, hours, minutes, seconds);
        }
        if (weeks == 0 && days == 0 && hours > 0) {
            return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
        }
        if (weeks == 0 && days == 0 && hours == 0 && minutes > 0) {
            return String.format("%02dm %02ds", minutes, seconds);
        }
        if (weeks == 0 && days == 0 && hours == 0 && minutes == 0) {
            return String.format("%02ds", seconds);
        }
        if (weeks == 0 && days == 0 && hours == 0 && minutes == 0 && seconds == 0) {
            return String.format("%04dms", ms);
        }
        return "00";
    }

    private long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

    private String getElapsedTimeAsString() {
        return makeTimeString(getElapsedTime());
    }

    private int randomNum(int i, int k) {
        return Calculations.random(i, k);
    }

    private Player player() {
        return Players.getLocal();
    }

    /*
     * =========================================
     *             GUI/Paint
     * =========================================
     */

    @Override
    public void onPaint(Graphics g) {
        SwingUtilities.invokeLater(() -> {
            g.setColor(Color.LIGHT_GRAY);
            g.fillRect(6, 300, 500, 220);
            g.setColor(Color.BLACK);
            g.drawRect(6, 300, 500, 220);
            g.setColor(Color.BLUE);
            g.setFont(new Font("Arial", Font.BOLD, 15));
            g.drawString("JavaNjoyer Fruit Stall Stealer", 60, 330);
            g.drawLine(62, 332, 242, 332);
            g.drawString(s, 12, 370);
            if (image != null) {
                g.drawImage(image, 280, 350, null);
            }
            g.drawString("Fruit Stolen: " + fruitStolen, 12, 390);
            g.drawString("Levels gained: " + closedDialogues, 12, 410);
            g.drawString("Run time: " + getElapsedTimeAsString(), 12, 430);
            g.drawString("Thieving Lvl: " + Skills.getRealLevel(Skill.THIEVING), 12, 450);
            g.drawString("Exp/Hr: " + SkillTracker.getGainedExperiencePerHour(Skill.THIEVING), 12, 470);
            g.drawString("Time Till Level: " + makeTimeString(SkillTracker.getTimeToLevel(Skill.THIEVING)), 12, 490);
        });
    }

    private void createGUI() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame();
            frame.setTitle("FruitStallStealer Setup");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setLocationRelativeTo(Client.getCanvas());
            JPanel mainPanel = new JPanel(new GridLayout(4, 1));
            JLabel efficiencyLabel = new JLabel();
            efficiencyLabel.setText("Efficiency Rate:");
            JPanel radioPanel = new JPanel();
            radioPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Choose One"));

            JRadioButton maxRadioBtn = new JRadioButton("Max");
            JRadioButton highRadioBtn = new JRadioButton("High");
            JRadioButton mediumRadioBtn = new JRadioButton("Medium");
            JRadioButton lowRadioBtn = new JRadioButton("Low");

            ButtonGroup radioGroup = new ButtonGroup();
            radioGroup.add(maxRadioBtn);
            radioGroup.add(highRadioBtn);
            radioGroup.add(mediumRadioBtn);
            radioGroup.add(lowRadioBtn);

            maxRadioBtn.addItemListener(e -> efficiencyRate = Efficiency.MAX);
            highRadioBtn.addItemListener(e -> efficiencyRate = Efficiency.HIGH);
            mediumRadioBtn.addItemListener(e -> efficiencyRate = Efficiency.MEDIUM);
            lowRadioBtn.addItemListener(e -> efficiencyRate = Efficiency.LOW);

            radioPanel.add(efficiencyLabel);
            radioPanel.add(maxRadioBtn);
            radioPanel.add(highRadioBtn);
            radioPanel.add(mediumRadioBtn);
            radioPanel.add(lowRadioBtn);

            mainPanel.add(radioPanel);

            //Target level panel
            JPanel centerPanel = new JPanel();
            centerPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Target Level"));
            JLabel targetLvlLbl = new JLabel("Target level:");
            JTextField targetLvlTxt = new JTextField();
            targetLvlTxt.setPreferredSize(new Dimension(100, 20));
            centerPanel.add(targetLvlLbl);
            centerPanel.add(targetLvlTxt);
            mainPanel.add(centerPanel);

            // Add first new Bordered Group
            JPanel playTimePanel = new JPanel();
            playTimePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Runtime (minutes) (60-90 recommended)"));
            JLabel playTimeLbl = new JLabel("Max runtime:");
            JTextField playTimeTxt = new JTextField();
            playTimeTxt.setPreferredSize(new Dimension(100, 20));
            playTimePanel.add(playTimeLbl);
            playTimePanel.add(playTimeTxt);
            mainPanel.add(playTimePanel);

            JPanel buttonPanel = new JPanel();

            JButton startButton = new JButton("Start!");
            startButton.addActionListener(l -> {
                isRunning = true;
                try {
                    parseMaxTimeInput(playTimeTxt.getText());
                    parseTargetLvlInput(targetLvlTxt.getText());
                } catch (InvalidConfigurationException e) {
                    log("Error occured: " + e.getMessage() + "!\n Terminating script...");
                    invalidInput = true;
                }
                log("Chosen efficiency rate: " + efficiencyRate);
                log("Chosen target level: " + targetLevel);
                log("Chosen runtime: " + MAX_TIME);
                frame.dispose();
            });

            buttonPanel.add(startButton);

            frame.getContentPane().add(mainPanel, BorderLayout.CENTER);
            //frame.getContentPane().add(centerPanel, BorderLayout.CENTER);
            frame.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
            frame.pack();
            frame.setVisible(true);
        });
    }

    private void parseMaxTimeInput(String input) throws InvalidConfigurationException{
        int parsedTime;
        if (input.isEmpty()) {
            throw new InvalidConfigurationException("Playtime cannot be empty!");
        }
        try {
            parsedTime = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new InvalidConfigurationException("Invalid input: " + input + ". Please enter a valid number between 1 and 600!");
        }

        if (parsedTime <= 0 || parsedTime > 600) {
            throw new InvalidConfigurationException("Time must be between 1 and 600 minutes! You entered: " + parsedTime);
        }
        MAX_TIME = parsedTime * 60 * 1000;
        log("Will terminate script in " + parsedTime + " minutes.");
        log("MS Value: " + MAX_TIME);
    }
    private void parseTargetLvlInput(String input) throws InvalidConfigurationException {
        int parsedInput;
        if (input.isEmpty()) {
            throw new InvalidConfigurationException("Playtime cannot be empty!");
        }
        try {
            parsedInput = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new InvalidConfigurationException("Invalid input: " + input + ". Please enter a valid number between 25 and 99!");
        }

        if (parsedInput <= 25 || parsedInput > 99) {
            throw new InvalidConfigurationException("Target level must be between 25 and 99 minutes! You entered: " + parsedInput);
        }
        targetLevel = parsedInput;
        log("Set target level to " + targetLevel);
    }
}