/*************
 * 
 * This system combines different trading strategies, showing the signal generated by each one.
 * The first strategy is the algorithmic trading strategy, whilst the second is the ANN strategy.
 * 
 ******/
package stockforecasting.tradingsystems;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import stockforecasting.indicators.FischerTransform;
import stockforecasting.indicators.MAIndicator;
import stockforecasting.indicators.MovingAverages;
import stockforecasting.indicators.RSI;
import stockforecasting.ml.ANN;
import stockforecasting.utilities.DataFormat;


public class TradingSystem {
    
    DataFormat df = new DataFormat();
    MovingAverages ma = new MovingAverages();
    ANN net = null;
    
    int arrayLength;
    int skip_array_size;
    int test_size;

    // This is the SMA used to smoothen the curve before processing. A value of 1 means it is not smoothened.
    int[] SMA_window_sizes = { 1/*,5,10,15,20*/ };
    
    double open_buy, close_buy, open_sell, close_sell, buy_profit, sell_profit, buy_ROI, sell_ROI;
    boolean buy_open, sell_open;
    double open_buy_ann, close_buy_ann, open_sell_ann, close_sell_ann, buy_profit_ann, sell_profit_ann, buy_ROI_ann, sell_ROI_ann;
    boolean buy_open_ann, sell_open_ann;
    
    public enum SIGNAL{
        BUY, SELL, NONE
    }
    
    int ma_window_size = 100;
    double[] ma_window = new double[ma_window_size];
    int rsi_window_size = 11;
    double[] rsi_window = new double[rsi_window_size];
    int ann_window_size = 20;
    double[] ann_window = new double[ann_window_size];
    
    RSI r = new RSI();
    MAIndicator mi = new MAIndicator();
    FischerTransform ft = new FischerTransform();
    FischerTransform ft_ann = new FischerTransform();
    
    SIGNAL fisherSignal;
    SIGNAL[] maSignal;
    SIGNAL rsiSignal;
    double annSignal;
    
    List<String> csvFile = new ArrayList<String>();
    
    public TradingSystem(String dataFilePath, int _total_array, int _target_array){

        arrayLength = _total_array;
        skip_array_size = _target_array;
        test_size = arrayLength - skip_array_size;

        //DeSerialize ANN
        try
        {
           FileInputStream fileIn = new FileInputStream("data/ANN_serialized_temp.ser");
           ObjectInputStream in = new ObjectInputStream(fileIn);
           net = (ANN) in.readObject();
           in.close();
           fileIn.close();
        }catch(IOException i)
        {
           i.printStackTrace();
           return;
        }catch(ClassNotFoundException c)
        {
           System.out.println("ANN class not found");
           c.printStackTrace();
           return;
        }
        
        //Import Data
        csvFile.add(dataFilePath);

        double input[] = new double[test_size];
        double[] SMA;
        
        //Go through each data set
        for (String s : csvFile){
            //convert CSV data to array of double
            double[] input_pre = df.fileToArray(s, arrayLength);
            System.arraycopy(input_pre, skip_array_size, input, 0, test_size);
            
            //Loop through different SMA window sizes
            for (int i=0; i<SMA_window_sizes.length; i++){
                SMA = ma.SMA(input, SMA_window_sizes[i]);
                //System.out.print(SMA_window_sizes[i]);
                beginAlgorithmicTrading(input, SMA);
                ft.reset();
                mi.reset();
            }
            
            beginTradingANN(input);
        }
    }
    
    //Values are entered sequentially into each system.
    //The data is processed dynamically and signal is returned from each system.
    public void beginAlgorithmicTrading(double[] input, double[] SMA){
        
        open_buy = 0;
        close_buy = 0;
        open_sell = 0;
        close_sell = 0;
        buy_open = false;
        sell_open = false;
     
        for (int i=ma_window_size; i<SMA.length;i++){
            
            //********* Indicators
            
            //Moving Averages Indicator
            System.arraycopy(SMA, (i-ma_window_size), ma_window, 0, ma_window_size);
            maSignal = mi.run(ma_window);
            
            //RSI Indicator
            System.arraycopy(SMA, (i-rsi_window_size), rsi_window, 0, rsi_window_size);
            rsiSignal = r.run(rsi_window);
            
            //Fischer Transform Indicator
            fisherSignal = ft.run(SMA[i],SMA[i-1]);
            
            if (i % 15 == 0)
                mi.RegimeSwitch(ma_window);
            
//            System.out.println("*** Day number "+i);
//            System.out.println("Fisher: "+fisherSignal);
//            System.out.println("MA Buy: "+maSignal[0]);
//            System.out.println("MA Sell: "+maSignal[1]);
//            System.out.println("RSI: "+rsiSignal);
            
            //If 2 out of 3 signal BUY, then open buy position
            if ((fisherSignal == SIGNAL.BUY) && (maSignal[0] == SIGNAL.BUY)
                    || (fisherSignal == SIGNAL.BUY) && (rsiSignal == SIGNAL.BUY)
                    || (maSignal[0] == SIGNAL.BUY) && (rsiSignal == SIGNAL.BUY))
            {
                  if (!buy_open){
                    open_buy += input[i];
                    buy_open = true;
                    //System.out.println("Open buy: "+input[i]);
                    }
            }
            else if (buy_open){
                close_buy += input[i];
                buy_open = false;
                buy_profit = close_buy - open_buy;
                buy_ROI = Math.log(close_buy / open_buy );
                //System.out.println("Close buy: "+input[i]);
            }
            
            //If 2 out of 3 signal SELL, then open sell position
             if ((fisherSignal == SIGNAL.SELL) && (maSignal[1] == SIGNAL.SELL)
                    || (fisherSignal == SIGNAL.SELL) && (rsiSignal == SIGNAL.SELL)
                    || (maSignal[1] == SIGNAL.SELL) && (rsiSignal == SIGNAL.SELL))
             {
                  if (!sell_open){
                    open_sell += input[i];
                    sell_open = true;
                    //System.out.println("Open sell: "+input[i]);
                  }
            }
            else if (sell_open){
                close_sell += input[i];
                sell_open = false;
                sell_profit = open_sell - close_sell;
                sell_ROI = Math.log(open_sell / close_sell);
                //System.out.println("Close sell: "+input[i]);
            }
        }
        
        System.out.println("Algorithmic Trading System Results:");
        System.out.println("Buy ROI: " + Math.round(buy_ROI*10000.0)/10000.0);
        System.out.println("Sell ROI: " + Math.round(sell_ROI*10000.0)/10000.0);
        System.out.println("Total ROI: " + Math.round((buy_ROI + sell_ROI)*10000.0)/10000.0);
        System.out.println("Buy profit: " + Math.round(buy_profit*10000.0)/10000.0);
        System.out.println("Sell profit: " + Math.round(sell_profit*10000.0)/10000.0);
        System.out.println("Total profit: " + Math.round((buy_profit+sell_profit)*10000.0)/10000.0);
        System.out.println(MessageFormat.format("{0},{1},{2},{3},{4},{5}",buy_ROI, sell_ROI, buy_ROI + sell_ROI, buy_profit, sell_profit, buy_profit+sell_profit));
        System.out.println();
    }
    
    
    public void beginTradingANN(double[] input){
        open_buy_ann = 0;
        close_buy_ann = 0;
        open_sell_ann = 0;
        close_sell_ann = 0;
        buy_open_ann = false;
        sell_open_ann = false;
        
        for (int i=ann_window_size; i<input.length;i++){
            //Place past 20 values in ann_window
            System.arraycopy(input, (i-ann_window_size), ann_window, 0, ann_window_size);
            ann_window = ft_ann.convert(ann_window);
            ann_window = ma.SMA(ann_window, 5);
            double[] annSignalTemp = net.run(ann_window);
            annSignal = Math.round(annSignalTemp[0]);
            
            if (annSignal == 0.0){
                if (buy_open_ann){
                    buy_open_ann =false;
                    close_buy_ann = input[i];
                    buy_profit_ann += close_buy_ann - open_buy_ann;
                    buy_ROI_ann += Math.log(close_buy_ann / open_buy_ann);
                }
                else if (!sell_open_ann){
                    sell_open_ann = true;
                    open_sell_ann = input[i];
                }
            }
            else if (annSignal == 1.0){
                if (sell_open_ann){
                    sell_open_ann = false;
                    close_sell_ann = input[i];
                    sell_profit_ann += open_sell_ann - close_sell_ann;
                    sell_ROI_ann += Math.log(open_sell_ann / close_sell_ann);
                }
                else if (!buy_open_ann){
                    buy_open_ann = true;
                    open_buy_ann = input[i];  
                }
            }
        }
        
        System.out.println("ANN Trading System");
        System.out.println("Buy ROI: " + Math.round(buy_ROI_ann*10000.0)/10000.0);
        System.out.println("Sell ROI: " + Math.round(sell_ROI_ann*10000.0)/10000.0);
        System.out.println("Total ROI: " + Math.round((buy_ROI_ann + sell_ROI_ann)*10000.0)/10000.0);
        System.out.println("Buy profit: " + Math.round(buy_profit_ann*10000.0)/10000.0);
        System.out.println("Sell profit: " + Math.round(sell_profit_ann*10000.0)/10000.0);
        System.out.println("Total profit: " + Math.round((buy_profit_ann+sell_profit_ann)*10000.0)/10000.0);
        System.out.println(MessageFormat.format("{0},{1},{2},{3},{4},{5}",buy_ROI_ann, sell_ROI_ann, buy_ROI_ann + sell_ROI_ann, buy_profit_ann, sell_profit_ann, buy_profit_ann+sell_profit_ann));
        System.out.println();
    }
}