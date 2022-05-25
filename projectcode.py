import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import os
from datetime import datetime
from sklearn.metrics import accuracy_score, mean_squared_error
from datetime import timedelta

# -------------------------------------------function definitions-----------------------------------------------------------
def updatedOrientAzimuthAngle(azimuth):
    x = -360+azimuth if azimuth > 360 else 360+azimuth if azimuth < 0 else azimuth
    return x
def updatedAzimuthAngle(azimuth):
    x = -360+azimuth if azimuth > 180 else 360+azimuth if azimuth < -180 else azimuth
    # x = azimuth
    return x
def newUpdatedAzimuthAngle(azimuth):
    x = 180-azimuth if azimuth<0 else azimuth
    return x
def getAccuracyScoreRef(df):
    print("-------------------------------------------------------------------------------")
    print("--------------Accuracy Score with Ref Measurement--------------------")
    print("O1\t\tO3\t\tO4")
    for threshold in [5, 10, 15, 20]:
        print("Threshold: "+str(threshold))
        for par in ['Azimuth', 'fu_Azimuth', 'Orient_Azimuth']:
            print(par)
            for i in range(1,5):
                if i != 2:
                    df_pred = df[i][par]
                    df_ref = df[2][par] 
                    df_pred = [ref if pred>= (ref-threshold) and pred<=(ref+threshold) else pred for pred, ref in zip(df_pred, df_ref)]
                    print("%.4f" % round(accuracy_score(np.array(df_ref).round(),np.array(df_pred).round(), normalize=False)/ len(df_pred), 4), end="\t\t")
            print("\n")
        print("-------------------------------------------------------------------------------")
def getAccuracyScoreGT(df):
    print("-------------------------------------------------------------------------------")
    print("--------------Accuracy Score with Ground Truth--------------------")
    print("O1\t\tO2\t\tO3\t\tO4")
    for threshold in [5, 10, 15, 20]:
        print("Threshold: "+str(threshold))
        for par in ['Azimuth', 'fu_Azimuth', 'Orient_Azimuth']:
            print(par)
            for i in range(1,5):
                df_pred = df[i][par]
                df_ref = df[5][par] 
                df_pred = [ref if pred>= (ref-threshold) and pred<=(ref+threshold) else pred for pred, ref in zip(df_pred, df_ref)]
                print("%.4f" % round(accuracy_score(np.array(df_ref).round(),np.array(df_pred).round(), normalize=False)/ len(df_pred), 4), end="\t\t")
            print("\n")
        print("-------------------------------------------------------------------------------")

def getFeaturePlots(df, title, pth):
    for feature in ['Orient_Azimuth', 'Original_Azimuth', 'Azimuth', 'fu_Azimuth', 'fu_Azimuth_org','Original_AzimuthNew']:
        plt.figure(figsize=(7,5), facecolor='white')
        plt.plot(df[1][feature][0:3000], label = 'O1')
        plt.plot(df[2][feature][0:3000], label = 'O2')
        plt.plot(df[3][feature][0:3000], label = 'O3')
        plt.plot(df[4][feature][0:3000], label = 'O4')
        plt.plot(df[5][feature][0:3000], label = 'GT')
        plt.xlabel('Number of Samples')
        plt.ylabel('Azimuth in degrees')
        if title == 'Hand':
            plt.title(title)
        else:
            plt.title(title+' Pocket')
        plt.legend()
        plt.grid()
        if feature in ['Orient_Azimuth', 'Original_Azimuth']:
                plt.ylim(0, 360)
        elif feature in ['Azimuth', 'fu_Azimuth', 'fu_Azimuth_org','Original_AzimuthNew']:
                plt.ylim(-180, 180)
        plt.savefig(pth+'\\OrientImages\\'+feature+'_'+title+'.png', dpi=500)
# ----------------------------------function definitions End------------------------------------------------------






df_total = {}
# ------------------------------------change the path to the relevant position of Smartphone------------------------------------
path_data = 'E:\\masterproject\\Project\\data\\Trouser\\'
files = os.listdir(path_data)
format = "%M:%S"
position = os.path.basename(os.path.dirname(path_data))
for j in [0]:
    i=0
    plt.figure(figsize=(20,20))
    ref=[]
    for file in range(0,5):
        i=i+1
        if i<5:
            df = pd.read_csv(path_data+files[file])
            # -----------------------------------Creating the Offset-----------------------------------
            if position == 'Trouser':
                df = df[5000:8000]
            else:
                df = df[12000:15000]
            # -----------------------------------------------------------------------------------------
            df['Time in Seconds'] = [datetime.strptime(str(x)[0:5], format) for x in df['Time in Seconds']]
            df = df.reset_index()
            df_accel_mean = df
            df_accel_mean = df_accel_mean.set_index(df_accel_mean['Time in Seconds']).resample('1.6S').mean()
            Acceleration = df['Acceleration_Z'].to_list()
            Orient_Azimuth = df['OrientSensor_Azimuth'].to_list()
            Orient_Pitch = df['OrientSensor_Pitch'].to_list()
            Orient_Roll = df['OrientSensor_Roll'].to_list()
            Azimuth = df['AccelOrient_Azimuth'].to_list()
            Pitch = df['AccelOrient_Pitch'].to_list()
            Roll = df['AccelOrient_Roll'].to_list()
            Original_Azimuth = df['OrientSensor_Azimuth'].to_list()
            Original_AzimuthNew = df['AccelOrient_Azimuth'].to_list()
            fu_Azimuth = df['FusedOrientation_Azimuth'].to_list()
            fu_Pitch = df['FusedOrientation_Pitch'].to_list()
            fu_Roll = df['FusedOrientation_Roll'].to_list()
            fu_Azimuth_org = df['FusedOrientation_Azimuth'].to_list()
            # -------------------------------Starting SOMDA Algorithm--------------------------------------------------------------
            for k in range(0, len(Acceleration)):
                for time, acc in zip(df_accel_mean.index, df_accel_mean['Acceleration_Z']):
                    val = list(df['Time in Seconds'][k:k+1])[0] - time
                    if  (val>=timedelta(seconds=0)) and (val< timedelta(seconds=1.6)):
                        Acceleration[k] = acc
                if position == 'Trouser':
                    if Orient_Pitch[k] < 0:
                        Orient_Azimuth[k] = updatedOrientAzimuthAngle(Orient_Azimuth[k] + Orient_Roll[k])
                        Orient_Azimuth[k] = updatedOrientAzimuthAngle(Orient_Azimuth[k] + 180 if Acceleration[k] > 0 else Orient_Azimuth[k])
                    else:
                        Orient_Azimuth[k] = updatedOrientAzimuthAngle(Orient_Azimuth[k] - Orient_Roll[k])
                        Orient_Azimuth[k] = updatedOrientAzimuthAngle(Orient_Azimuth[k] + 180 if Acceleration[k] < 0 else Orient_Azimuth[k])
                    Orient_Azimuth[k] = 360 if Orient_Azimuth[k]<6 else Orient_Azimuth[k]
                    if Pitch[k] < 0:
                        Azimuth[k] = Azimuth[k] + Roll[k]
                        Azimuth[k] = updatedAzimuthAngle(Azimuth[k] + 180 if Acceleration[k] > 0 else Azimuth[k])
                    else:
                        Azimuth[k] = Azimuth[k] - Roll[k]
                        Azimuth[k] = updatedAzimuthAngle(Azimuth[k] + 180 if Acceleration[k] < 0 else Azimuth[k])
                    if fu_Pitch[k] < 0:
                        fu_Azimuth[k] = (fu_Azimuth[k] + fu_Roll[k])
                        fu_Azimuth[k] = updatedAzimuthAngle(fu_Azimuth[k] + 180 if Acceleration[k] > 0 else fu_Azimuth[k])
                    else:
                        fu_Azimuth[k] = (fu_Azimuth[k] - fu_Roll[k])
                        fu_Azimuth[k] = updatedAzimuthAngle(fu_Azimuth[k] + 180 if Acceleration[k] < 0 else fu_Azimuth[k])
                else:
                    if Orient_Pitch[k] > 0:
                        Orient_Azimuth[k] = updatedOrientAzimuthAngle(Orient_Azimuth[k] + Orient_Roll[k])
                        Orient_Azimuth[k] = updatedOrientAzimuthAngle(Orient_Azimuth[k] + 180 if Acceleration[k] > 0 else Orient_Azimuth[k])
                    else:
                        Orient_Azimuth[k] = updatedOrientAzimuthAngle(Orient_Azimuth[k] - Orient_Roll[k])
                        Orient_Azimuth[k] = updatedOrientAzimuthAngle(Orient_Azimuth[k] + 180 if Acceleration[k] < 0 else Orient_Azimuth[k])
                    Orient_Azimuth[k] = 360 if Orient_Azimuth[k]<6 else Orient_Azimuth[k]
                    if Pitch[k] > 0:
                        Azimuth[k] = Azimuth[k] + Roll[k]
                        Azimuth[k] = updatedAzimuthAngle(Azimuth[k] + 180 if Acceleration[k] > 0 else Azimuth[k])
                    else:
                        Azimuth[k] = Azimuth[k] - Roll[k]
                        Azimuth[k] = updatedAzimuthAngle(Azimuth[k] + 180 if Acceleration[k] < 0 else Azimuth[k])
                    if fu_Pitch[k] > 0:
                        fu_Azimuth[k] = (fu_Azimuth[k] + fu_Roll[k])
                        fu_Azimuth[k] = updatedAzimuthAngle(fu_Azimuth[k] + 180 if Acceleration[k] > 0 else fu_Azimuth[k])
                    else:
                        fu_Azimuth[k] = (fu_Azimuth[k] - fu_Roll[k])
                        fu_Azimuth[k] = updatedAzimuthAngle(fu_Azimuth[k] + 180 if Acceleration[k] < 0 else fu_Azimuth[k])
        else:
            Orient_Azimuth = Original_Azimuth=290
            Original_AzimuthNew = -360+Orient_Azimuth if Orient_Azimuth>180 else 360+Orient_Azimuth if Orient_Azimuth<-180 else Orient_Azimuth
            Azimuth = fu_Azimuth = fu_Azimuth_org = Original_AzimuthNew
        # ----------------------------------------------End of SOMDA algorithm--------------------------------------------------------
        df_new = pd.DataFrame({'Acceleration':Acceleration, 'Original_Azimuth':Original_Azimuth, 'Original_AzimuthNew':Original_AzimuthNew,
        'Orient_Pitch':Orient_Pitch,'Orient_Roll':Orient_Roll,'Orient_Azimuth':Orient_Azimuth, 
        'Pitch':Pitch, 'Roll':Roll,'Azimuth':Azimuth, 'fu_Pitch': fu_Pitch, 'fu_Roll': fu_Roll,'fu_Azimuth': fu_Azimuth, 'fu_Azimuth_org':fu_Azimuth_org, 'Time': df['Time in Seconds']})
        df_total[i] = df_new
        for k, column in zip(range(0,13), df_new.columns):
            plt.subplot(5,3,k+1)
            if i < 5:
                plt.plot(df_new[column], label=f"O{i}")
            elif column in ['Azimuth', 'Original_AzimuthNew','Orient_Azimuth', 'fu_Azimuth', 'fu_Azimuth_org']:
                plt.plot(df_new[column], label='GT')
            plt.title(column, color='white')
            if column in ['Orient_Azimuth', 'Original_Azimuth']:
                plt.ylim(0, 360)
            elif column in ['Azimuth', 'fu_Azimuth', 'fu_Azimuth_org','Original_AzimuthNew']:
                plt.ylim(-180, 180)
            plt.xticks(color='white')
            plt.yticks(color='white')
            plt.grid()
            plt.legend()
plt.savefig(path_data+'\\OrientImages\\'+'total.png')
getAccuracyScoreGT(df_total)
getAccuracyScoreRef(df_total)
getFeaturePlots(df=df_total, title=position, pth=path_data)

            