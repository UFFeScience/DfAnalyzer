START TRANSACTION;

-- sequences
CREATE SEQUENCE "df_id_seq" as integer START WITH 1;
CREATE SEQUENCE "version_id_seq" as integer START WITH 1;
CREATE SEQUENCE "dt_id_seq" as integer START WITH 1;
CREATE SEQUENCE "program_id_seq" as integer START WITH 1;
CREATE SEQUENCE "ds_id_seq" as integer START WITH 1;
CREATE SEQUENCE "dd_id_seq" as integer START WITH 1;
CREATE SEQUENCE "extractor_id_seq" as integer START WITH 1;
CREATE SEQUENCE "ecombination_id_seq" as integer START WITH 1;
CREATE SEQUENCE "att_id_seq" as integer START WITH 1;
CREATE SEQUENCE "dfe_id_seq" as integer START WITH 1;
CREATE SEQUENCE "task_id_seq" as integer START WITH 1;
CREATE SEQUENCE "file_id_seq" as integer START WITH 1;
CREATE SEQUENCE "performance_id_seq" as integer START WITH 1;
CREATE SEQUENCE "annotation_id_seq" as integer START WITH 1;

-- tables
CREATE TABLE dataflow(
	id INTEGER DEFAULT NEXT VALUE FOR "df_id_seq" NOT NULL,
	tag VARCHAR(50) NOT NULL,
	PRIMARY KEY ("id")
);

-- Origin/generator: quem escreveu esta DEFINICAO. origin e 'manual', 'generated' ou 'curated'; NULL = o cliente nao informou.
CREATE TABLE dataflow_version(
	version INTEGER DEFAULT NEXT VALUE FOR "version_id_seq" NOT NULL,
	df_id INTEGER NOT NULL,
	origin VARCHAR(10),
	generator VARCHAR(200),
	PRIMARY KEY ("version"),
	FOREIGN KEY ("df_id") REFERENCES dataflow("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- Eixo de execucao. dataflow_version versiona a DEFINICAO do pipeline; dataflow_execution identifica uma RODADA.
CREATE TABLE dataflow_execution(
	id INTEGER DEFAULT NEXT VALUE FOR "dfe_id_seq" NOT NULL,
	df_version INTEGER NOT NULL,
	tag VARCHAR(100) NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY ("df_version") REFERENCES dataflow_version("version") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE data_transformation(
	id INTEGER DEFAULT NEXT VALUE FOR "dt_id_seq" NOT NULL,
	df_id INTEGER NOT NULL,
	tag VARCHAR(50) NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY ("df_id") REFERENCES dataflow("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE program(
	id INTEGER DEFAULT NEXT VALUE FOR "program_id_seq" NOT NULL,
	df_id INTEGER NOT NULL,
	name VARCHAR(200) NOT NULL,
	path VARCHAR(500) NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY ("df_id") REFERENCES dataflow("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE use_program(
	dt_id INTEGER NOT NULL,
	program_id INTEGER NOT NULL,
	PRIMARY KEY ("dt_id","program_id"),
	FOREIGN KEY ("dt_id") REFERENCES data_transformation("id") ON DELETE CASCADE ON UPDATE CASCADE,
	FOREIGN KEY ("program_id") REFERENCES program("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE data_set(
	id INTEGER DEFAULT NEXT VALUE FOR "ds_id_seq" NOT NULL,
	df_id INTEGER NOT NULL,
	tag VARCHAR(50) NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY ("df_id") REFERENCES dataflow("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE data_dependency(
	id INTEGER DEFAULT NEXT VALUE FOR "dd_id_seq" NOT NULL,
	previous_dt_id INTEGER,
	next_dt_id INTEGER,
	ds_id INTEGER NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY ("previous_dt_id") REFERENCES data_transformation("id") ON DELETE CASCADE ON UPDATE CASCADE,
	FOREIGN KEY ("next_dt_id") REFERENCES data_transformation("id") ON DELETE CASCADE ON UPDATE CASCADE,
	FOREIGN KEY ("ds_id") REFERENCES data_set("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE extractor(
	id INTEGER DEFAULT NEXT VALUE FOR "extractor_id_seq" NOT NULL,
	ds_id INTEGER NOT NULL,
	tag VARCHAR(20) NOT NULL,
	cartridge VARCHAR(20) NOT NULL,
	extension VARCHAR(20) NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY ("ds_id") REFERENCES data_set("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE extractor_combination(
	id INTEGER DEFAULT NEXT VALUE FOR "ecombination_id_seq" NOT NULL,
	ds_id INTEGER NOT NULL,
	outer_ext_id INTEGER NOT NULL,
	inner_ext_id INTEGER NOT NULL,
	keys VARCHAR(100) NOT NULL,
	key_types VARCHAR(100) NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY ("ds_id") REFERENCES data_set("id") ON DELETE CASCADE ON UPDATE CASCADE,
	FOREIGN KEY ("outer_ext_id") REFERENCES extractor("id") ON DELETE CASCADE ON UPDATE CASCADE,
	FOREIGN KEY ("inner_ext_id") REFERENCES extractor("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE attribute(
	id INTEGER DEFAULT NEXT VALUE FOR "att_id_seq" NOT NULL,
	ds_id INTEGER NOT NULL,
	extractor_id INTEGER,
	name VARCHAR(30),
	type VARCHAR(15),
	PRIMARY KEY ("id"),
	FOREIGN KEY ("ds_id") REFERENCES data_set("id") ON DELETE CASCADE ON UPDATE CASCADE,
	FOREIGN KEY ("extractor_id") REFERENCES extractor("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE task(
	id INTEGER DEFAULT NEXT VALUE FOR "task_id_seq" NOT NULL,
	identifier INTEGER NOT NULL,
	df_version INTEGER NOT NULL,
	-- Anulavel de proposito: uma falha ao resolver a execucao nao pode derrubar a ingestao inteira (risco R5).
	df_exec INTEGER,
	dt_id INTEGER NOT NULL,
	status VARCHAR(10),
	workspace VARCHAR(500),
	computing_resource VARCHAR(100),
	output_msg TEXT,
	error_msg TEXT,
	PRIMARY KEY ("id"),
	FOREIGN KEY ("df_version") REFERENCES dataflow_version("version") ON DELETE CASCADE ON UPDATE CASCADE,
	FOREIGN KEY ("df_exec") REFERENCES dataflow_execution("id") ON DELETE CASCADE ON UPDATE CASCADE,
	FOREIGN KEY ("dt_id") REFERENCES data_transformation("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE file(
	id INTEGER DEFAULT NEXT VALUE FOR "file_id_seq" NOT NULL,
	task_id INTEGER NOT NULL,
	name VARCHAR(200) NOT NULL,
	path VARCHAR(500) NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY ("task_id") REFERENCES task("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- Anotacoes do pesquisador: o caderno de laboratorio dentro da ferramenta.
CREATE TABLE annotation(
	id INTEGER DEFAULT NEXT VALUE FOR "annotation_id_seq" NOT NULL,
	df_id INTEGER NOT NULL,
	target VARCHAR(200) NOT NULL,
	body TEXT NOT NULL,
	author VARCHAR(100),
	created_at VARCHAR(30) NOT NULL,
	PRIMARY KEY ("id"),
	FOREIGN KEY ("df_id") REFERENCES dataflow("id") ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE performance(
	id INTEGER DEFAULT NEXT VALUE FOR "performance_id_seq" NOT NULL,
	task_id INTEGER NOT NULL,	
	subtask_id INTEGER,	
	method VARCHAR(30) NOT NULL,
	description VARCHAR(200),
	starttime VARCHAR(30),
	endtime VARCHAR(30),
	invocation TEXT,
	PRIMARY KEY ("id"),
	FOREIGN KEY ("task_id") REFERENCES task("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- procedures
-- DROP FUNCTION insertDataflow;
CREATE FUNCTION insertDataflow (v_tag VARCHAR(50))
RETURNS INTEGER
BEGIN
	DECLARE v_df_id INTEGER;
    SELECT df.id INTO v_df_id FROM dataflow df WHERE df.tag=v_tag;
    IF(v_df_id IS NULL) THEN
    	SELECT NEXT VALUE FOR "df_id_seq" into v_df_id;
    	INSERT INTO dataflow(id,tag) VALUES (v_df_id,v_tag);
	END IF;
	RETURN v_df_id;
END;

-- DROP FUNCTION insertDataflowVersion;
CREATE FUNCTION insertDataflowVersion (vdf_id INTEGER)
RETURNS INTEGER
BEGIN
	DECLARE version_id INTEGER;
	SELECT NEXT VALUE FOR "version_id_seq" into version_id;
    INSERT INTO dataflow_version(version,df_id) VALUES (version_id,vdf_id);
	RETURN version_id;
END;

-- DROP FUNCTION insertDataTransformation;
CREATE FUNCTION insertDataTransformation (vdf_id INTEGER, vtag VARCHAR(50))
RETURNS INTEGER
BEGIN
	DECLARE vdt_id INTEGER;
    SELECT id INTO vdt_id FROM data_transformation WHERE df_id = vdf_id AND tag = vtag;
    IF(vdt_id IS NULL) THEN
    	SELECT NEXT VALUE FOR "dt_id_seq" into vdt_id;
    	INSERT INTO data_transformation(id,df_id,tag) VALUES (vdt_id,vdf_id,vtag);
	END IF;
	RETURN vdt_id;
END;

-- DROP FUNCTION insertProgram;
CREATE FUNCTION insertProgram (vdf_id INTEGER, vdt_id INTEGER, vname VARCHAR(200), vpath VARCHAR(500))
RETURNS INTEGER
BEGIN
	DECLARE vprogram_id INTEGER;
    SELECT id INTO vprogram_id FROM program p WHERE df_id = vdf_id AND name = vname AND path = vpath;

    IF(vprogram_id IS NULL) THEN
    	SELECT NEXT VALUE FOR "program_id_seq" into vprogram_id;
    	INSERT INTO program(id,df_id,name,path) VALUES (vprogram_id,vdf_id,vname,vpath);
	END IF;
	INSERT INTO use_program(dt_id,program_id) VALUES (vdt_id,vprogram_id);
	RETURN vprogram_id;
END;

-- DROP FUNCTION insertDataSet;
CREATE FUNCTION insertDataSet (vdf_id INTEGER, vdt_id INTEGER, vdep_dt_id INTEGER, vtag VARCHAR(500), vtype VARCHAR(10))
RETURNS INTEGER
BEGIN
	DECLARE vds_id INTEGER;
    SELECT id INTO vds_id FROM data_set ds WHERE df_id = vdf_id AND tag = vtag;

    IF(vds_id IS NULL) THEN
    	SELECT NEXT VALUE FOR "ds_id_seq" into vds_id;
    	INSERT INTO data_set(id,df_id,tag) VALUES (vds_id,vdf_id,vtag);
	END IF;

	IF(vdep_dt_id IS NOT NULL) THEN
		DECLARE vdd_id INTEGER;
		SELECT ds_id INTO vdd_id FROM data_dependency
		WHERE previous_dt_id = vdep_dt_id AND next_dt_id = vdt_id AND ds_id = vds_id;

		-- CORRECAO: faltava `AND ds_id = vds_id`.
		DECLARE vid INTEGER;
		SELECT id INTO vid FROM data_dependency
		WHERE previous_dt_id = vdep_dt_id AND next_dt_id IS NULL AND ds_id = vds_id;

		IF(vid IS NULL) THEN
			IF(vdd_id IS NULL) THEN
				DECLARE vdd_id INTEGER;
				SELECT NEXT VALUE FOR "dd_id_seq" into vdd_id;

				INSERT INTO data_dependency(id,previous_dt_id,next_dt_id,ds_id) VALUES (vdd_id,vdep_dt_id,vdt_id,vds_id);
			END IF;
		ELSE
			UPDATE data_dependency SET next_dt_id = vdt_id WHERE id = vid;
		END IF;
	ELSE
		DECLARE vdd_id INTEGER;
		SELECT NEXT VALUE FOR "dd_id_seq" into vdd_id;

		IF(vtype LIKE 'INPUT') THEN
			INSERT INTO data_dependency(id,previous_dt_id,next_dt_id,ds_id) VALUES (vdd_id,null,vdt_id,vds_id);
		ELSE
			INSERT INTO data_dependency(id,previous_dt_id,next_dt_id,ds_id) VALUES (vdd_id,vdt_id,null,vds_id);
		END IF;
	END IF;

	RETURN vds_id;
END;

-- DROP FUNCTION insertAttribute;
CREATE FUNCTION insertAttribute (dds_id INTEGER, vextractor_id INTEGER, vname VARCHAR(30), vtype VARCHAR(15))
RETURNS INTEGER
BEGIN
	DECLARE vid INTEGER;
    SELECT id INTO vid FROM attribute WHERE ds_id=dds_id AND name=vname;
    IF(vid IS NULL) THEN
    	SELECT NEXT VALUE FOR "att_id_seq" into vid;
    	INSERT INTO attribute(id,ds_id,extractor_id,name,type) VALUES (vid,dds_id,vextractor_id,vname,vtype);
	END IF;
	RETURN vid;
END;

-- DROP FUNCTION insertTask; DROP FUNCTION insertExecution; Abre (ou reencontra) uma execucao de uma versao do dataflow.
CREATE FUNCTION insertExecution (vdf_version INTEGER, vtag VARCHAR(100))
RETURNS INTEGER
BEGIN
	DECLARE vid INTEGER;

	SELECT e.id INTO vid FROM dataflow_execution e
	WHERE e.df_version = vdf_version AND e.tag = vtag;

	IF(vid IS NULL) THEN
		SELECT NEXT VALUE FOR "dfe_id_seq" INTO vid;
		INSERT INTO dataflow_execution(id, df_version, tag) VALUES (vid, vdf_version, vtag);
	END IF;

	RETURN vid;
END;

CREATE FUNCTION insertTask (videntifier INTEGER, vdf_tag VARCHAR(50), vdt_tag VARCHAR(50), vstatus VARCHAR(10), vworkspace VARCHAR(500), 
	vcomputing_resource VARCHAR(100), voutput_msg TEXT, verror_msg TEXT, vdf_exec_tag VARCHAR(100))
RETURNS INTEGER
BEGIN
	DECLARE vid INTEGER;
	DECLARE vvstatus VARCHAR(10);
	DECLARE vdf_version INTEGER;
	DECLARE vdt_id INTEGER;
	DECLARE vdf_exec INTEGER;

	-- Use MAX(version) to ensure we only get one (the newest) scalar value.
	SELECT MAX(dfv.version) INTO vdf_version
	FROM dataflow df, dataflow_version dfv
	WHERE df.id = dfv.df_id AND df.tag = vdf_tag;

	-- Use the version we just found to get the specific transformation ID.
	SELECT dt.id INTO vdt_id
	FROM dataflow df, data_transformation dt
	WHERE df.id = dt.df_id AND df.tag = vdf_tag AND dt.tag = vdt_tag;

	-- SELECT dfv.version, dt.id INTO vdf_version, vdt_id FROM dataflow df, data_transformation dt, dataflow_version as dfv WHERE df.id = dt.df_id AND dfv.df_id = df.id AND df.tag = vdf_tag AND dt.tag = vdt_tag.

	IF((vdf_version IS NOT NULL) AND (vdt_id IS NOT NULL)) THEN
		-- Resolve a execucao.
		IF((vdf_exec_tag IS NOT NULL) AND (vdf_exec_tag <> '')) THEN
			SET vdf_exec = insertExecution(vdf_version, vdf_exec_tag);
		ELSE
			SELECT MAX(e.id) INTO vdf_exec FROM dataflow_execution e WHERE e.df_version = vdf_version;
			IF(vdf_exec IS NULL) THEN
				SET vdf_exec = insertExecution(vdf_version, 'implicita');
			END IF;
		END IF;

		-- A execucao entra na chave: sem ela, a task 1 da segunda rodada era encontrada como a task 1 da primeira e a sobrescrevia, deixando linhas de dados de duas rodadas penduradas na mesma task.
		SELECT t.id, t.status INTO vid, vvstatus
		FROM task t
		WHERE t.df_version = vdf_version AND t.dt_id = vdt_id AND t.identifier = videntifier
		  AND t.df_exec = vdf_exec;

		IF(vid IS NULL) THEN
		    SELECT NEXT VALUE FOR "task_id_seq" into vid;
		    INSERT INTO task(id,identifier,df_version,df_exec,dt_id,status,workspace,computing_resource,output_msg,error_msg) 
		    VALUES (vid,videntifier,vdf_version,vdf_exec,vdt_id,vstatus,vworkspace,vcomputing_resource,voutput_msg,verror_msg);
	    ELSE
	    	UPDATE task
	    	SET status = vstatus, output_msg = voutput_msg, error_msg = verror_msg
	    	WHERE identifier = videntifier AND df_version = vdf_version AND dt_id = vdt_id
	    	  AND df_exec = vdf_exec;
		END IF;
    END IF;
	RETURN vid;
END;

-- DROP FUNCTION insertFile;
CREATE FUNCTION insertFile (vtask_id INTEGER, vname VARCHAR(200), vpath VARCHAR(500))
RETURNS INTEGER
BEGIN
	DECLARE vid INTEGER;
    SELECT id INTO vid FROM file WHERE name=vname AND path=vpath;
    IF(vid IS NULL) THEN
    	SELECT NEXT VALUE FOR "file_id_seq" into vid;
    	INSERT INTO file(id,task_id,name,path) VALUES (vid,vtask_id,vname,vpath);
	END IF;
	RETURN vid;
END;

-- DROP FUNCTION insertPerformance;
CREATE FUNCTION insertPerformance (vtask_id INTEGER, vsubtask_id INTEGER, vmethod VARCHAR(30), vdescription VARCHAR(200), vstarttime VARCHAR(30), vendtime VARCHAR(30), vinvocation TEXT)
RETURNS INTEGER
BEGIN
	DECLARE vid INTEGER;
	IF(vsubtask_id IS NULL) THEN
		SELECT id INTO vid FROM performance WHERE method=vmethod and task_id=vtask_id;
	ELSE
		SELECT id INTO vid FROM performance WHERE method=vmethod and task_id=vtask_id and subtask_id=vsubtask_id;
	END IF;
    
    IF(vid IS NULL) THEN
    	SELECT NEXT VALUE FOR "performance_id_seq" into vid;
    	INSERT INTO performance(id,task_id,subtask_id,method,description,starttime,endtime,invocation) VALUES (vid,vtask_id,vsubtask_id,vmethod,vdescription,vstarttime,vendtime,vinvocation);
	ELSE
    	UPDATE performance
    	SET endtime = vendtime, invocation = vinvocation
    	WHERE id = vid and endtime = 'null';
	END IF;
	RETURN vid;
END;


-- DROP FUNCTION insertExtractor;
CREATE FUNCTION insertExtractor (vtag VARCHAR(20), vds_id INTEGER, vcartridge VARCHAR(20), vextension VARCHAR(20))
RETURNS INTEGER
BEGIN
	DECLARE vid INTEGER;
    SELECT id INTO vid FROM extractor WHERE tag = vtag AND ds_id = vds_id AND cartridge = vcartridge AND extension = vextension;
    IF(vid IS NULL) THEN
    	SELECT NEXT VALUE FOR "extractor_id_seq" into vid;
    	INSERT INTO extractor(id,ds_id,tag,cartridge,extension) VALUES (vid,vds_id,vtag,vcartridge,vextension);
	END IF;
	RETURN vid;
END;

-- DROP FUNCTION insertExtractorCombination;
CREATE FUNCTION insertExtractorCombination (vouter_ext_id INTEGER, vinner_ext_id INTEGER, vds_id INTEGER, vkeys VARCHAR(100), vkey_types VARCHAR(100))
RETURNS INTEGER
BEGIN
	DECLARE vid INTEGER;
    SELECT id INTO vid FROM extractor_combination WHERE outer_ext_id = vouter_ext_id AND inner_ext_id = vinner_ext_id AND ds_id = vds_id;
    IF(vid IS NULL) THEN
    	SELECT NEXT VALUE FOR "ecombination_id_seq" into vid;
    	INSERT INTO extractor_combination(outer_ext_id,inner_ext_id,keys,key_types,ds_id) VALUES (vouter_ext_id,vinner_ext_id,vkeys,vkey_types,vds_id);
	END IF;
	RETURN vid;
END;

COMMIT;
