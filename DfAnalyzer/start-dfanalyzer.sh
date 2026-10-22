#!/bin/bash
clear
echo "Setting up environment variables"
SIMULATION_DIR=`pwd`
DFA_PROPERTIES=$SIMULATION_DIR/DfA.properties
DI_DIR=$SIMULATION_DIR
DB_CONFIG_FILE=database.conf
DFANALYZER_VERSION=1.0
echo "--------------------------------------------"
echo "Removing data from previous executions"
rm $DFA_PROPERTIES
# organizing provenance directories
rm -rf provenance/*
mkdir -p provenance
# cleaning up MonetDB directory
killall mserver5
killall monetdbd
sleep 3
#rm -rf data
rm prov-db.dump
rm -rf output/*
rm *.log
# configuring computational environment for application run
rm $DB_CONFIG_FILE
rm nodes.txt
# killing previous processes
# killall java
echo "--------------------------------------------"
echo "Configuring the file DfA.properties"
echo "di_dir="$DI_DIR >> $DFA_PROPERTIES
echo "dbms=MONETDB" >> $DFA_PROPERTIES
echo "db_server=localhost" >> $DFA_PROPERTIES
echo "db_port=50000" >> $DFA_PROPERTIES
echo "db_name=dataflow_analyzer" >> $DFA_PROPERTIES
echo "db_user=monetdb" >> $DFA_PROPERTIES
echo "db_password=monetdb" >> $DFA_PROPERTIES
echo "--------------------------------------------"
echo "Restoring MonetDB database..."
echo "localhost" >> $DB_CONFIG_FILE
unzip -q data-local.zip
clear
echo "--------------------------------------------"
echo "Starting database system..."
DATAPATH=$SIMULATION_DIR/data
lines=`cat $DB_CONFIG_FILE  | egrep -v "#"`
for i in `echo $lines`; do 
  host=`echo $i`
  # echo "cd $SIMULATION_DIR;monetdbd start $DATAPATH;monetdbd get all $DATAPATH;monetdb start dataflow_analyzer;monetdb status"
  # O listenaddr só vale se for definido antes do start.
  cd $SIMULATION_DIR;monetdbd set listenaddr=0.0.0.0 $DATAPATH;monetdbd start $DATAPATH;monetdbd get all $DATAPATH;monetdb start dataflow_analyzer;monetdb status
  # echo "cd $SIMULATION_DIR;killall monetdb;killall monetdbd;killall mserver5;monetdbd set port=54321 $DATAPATH; monetdbd start $DATAPATH;monetdbd get all $DATAPATH;monetdb start dataflow_analyzer;monetdb status"
  # ssh $host "cd $SIMULATION_DIR;monetdbd stop $DATAPATH;killall monetdb;killall monetdbd;killall mserver5;monetdbd set port=54321 $DATAPATH; monetdbd start $DATAPATH;monetdbd get all $DATAPATH;monetdb start dataflow_analyzer;monetdb status;" &
done
echo "--------------------------------------------"
echo "Applying schema migrations..."
sh $SIMULATION_DIR/migrate-database.sh
echo "--------------------------------------------"
echo "Starting DfAnalyzer..."
java -jar target/DfAnalyzer-$DFANALYZER_VERSION.jar