import requests
import os
from .ProvenanceObject import ProvenanceObject
from .dependency import Dependency
from .task_status import TaskStatus
from .dataset import DataSet
from .performance import Performance
from .execution import get_execution_tag
from .response import check_response
from datetime import datetime

dfa_url = os.environ.get('DFA_URL',"http://localhost:22000/")


class Task(ProvenanceObject):
    """This class defines a dataflow task."""
    def __init__(self, id, dataflow_tag, transformation_tag,
                 sub_id="", dependency=None, workspace="", resource="",
                 output="", error="", execution_tag=None):
        ProvenanceObject.__init__(self, transformation_tag)
        self._workspace = workspace
        self._resource = resource
        self._dependency = ""
        self._output = output
        self._error = error
        self._sets = []
        self._status = TaskStatus.READY.value
        self._dataflow = dataflow_tag.lower()
        self._execution = execution_tag or get_execution_tag()
        self._transformation = transformation_tag.lower()
        self._id = str(id)
        self._sub_id = sub_id
        self._performances = []
        self.dfa_url = dfa_url
        self.start_time = None
        self.end_time = None
        if isinstance(dependency, Task):
            dependency = Dependency([dependency._tag], [dependency._id])
            self._dependency = dependency.get_specification()

    def add_dependency(self, dependency):
        """ Add a dependency to the Task.

        Args:
            - dependency (:obj:`Dependency`): A :obj:`Dependency` object.
        """

        assert isinstance(dependency, Dependency), \
            "The dependency must be valid."
        self._dependency = dependency.get_specification()

    def set_datasets(self, datasets):
        """ Set the Task DataSets.

        Args:
            - dataset (:obj:`list`): A :obj:`list` containing :obj:`DataSet` objects.
        """
        assert isinstance(datasets, list), \
            "The parameter must be a list."
        for dataset in datasets:
            self.add_dataset(dataset)

    def add_dataset(self, dataset):
        """ Add a dataset to the Task.

        Args:
            - dataset (:obj:`DataSet`): A :obj:`DataSet` object.
        """
        assert isinstance(dataset, DataSet), "The dataset must be valid."
        self._sets.append(dataset.get_specification())

    def set_status(self, status):
        """ Change the Task Status.

        Args:
            - status (:obj:`TaskStatus`): A :obj:`TaskStatus` object.
        """
        assert isinstance(status, TaskStatus), \
            "The task status must be valid."
        self._status = status.value

    def begin(self):
        """ Send a post request to the Dataflow Analyzer API to store the Task.
        """        
        self.set_status(TaskStatus.RUNNING)
        self.start_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        self.save()      

    def end(self):
        """ Send a post request to the Dataflow Analyzer API to store the Task.
        """
        self.set_status(TaskStatus.FINISHED)
        self.end_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        performance = Performance(self.start_time, self.end_time)
        self._performances.append(performance.get_specification())
        self.save()   
        
    def save(self):
        """ Send a post request to the Dataflow Analyzer API to store the Task.
        """
        url = dfa_url + '/pde/task/json'
        message = self.get_specification()
        r = requests.post(url, json=message)
        print(r.status_code)
        check_response(r, f"task {self._id} de '{self._transformation}'")
        self._sets = []
