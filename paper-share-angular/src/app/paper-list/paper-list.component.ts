import { Component, OnInit, Input } from '@angular/core';
import { PaperService } from '../Service/paper.service';
import { PaperView } from '../model/paperView';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material';

@Component({
  selector: 'app-paper-list',
  templateUrl: './paper-list.component.html',
  styleUrls: ['./paper-list.component.css']
})
export class PaperListComponent implements OnInit {

  @Input() papers: Array<PaperView>;
  @Input() forUser: boolean;
  @Input() homePage: boolean;
  role: string;

  constructor(private paperService: PaperService, private router: Router, private snackBar: MatSnackBar) { }

  ngOnInit(): void {
    this.role = localStorage.getItem('role');
  }

  getUserPapers() {
    this.paperService.getUserPapers().subscribe(
      (response => {
        if (response !== null) {
          this.papers = response;
        }
      }),
      (error => {
        alert(error.error.message);
      })
    );
  }

  acceptPaper(paperName: string, index: number) {
    this.paperService.acceptPaper(paperName).subscribe(
      (response => {
        if (response !== null) {
          this.snackBar.open('Accepted!');
          this.papers.splice(0, index);
        }
      }),
      (error => {
        alert(error.error.message);
      })
    );
  }

  rejectPaper(paperName: string, index: number) {
    this.paperService.rejectPaper(paperName).subscribe(
      (response => {
        if (response !== null) {
          this.snackBar.open('Rejected!');
          this.papers.splice(0, index);
        }
      }),
      (error => {
        alert(error.error.message);
      })
    );
  }

  assignReview(title: string) {
    this.router.navigateByUrl('/assign-review/' + title);
  }

  openPaper(name: string) {
    window.open('/view-paper/' + name);
  }

  goToPaperReviews(paperName: string) {
    window.open('/united-reviews/' + paperName);
  }

  deletePaper(title: string, index: number) {
    this.paperService.deletePaper(title).subscribe(
      (response => {
        this.snackBar.open('Deleted paper: ' + title);
        this.papers.splice(index, 1);
      }),
      (error => {
        const err = JSON.parse(error.error);
        this.snackBar.open(err.message);
      })
    );

  }
  addPaper() {
    this.router.navigateByUrl('add');
  }
  edit(name: string) {
    this.router.navigateByUrl('add/' + name);
  }

  getHref(name: string) {
    return 'http://localhost:8080/api/papers/' + name + '/pdf';
  }

  sendToEditor(index: number) {
    this.paperService.sendToEditor(this.papers[index].title).subscribe(
      (response => {
        this.snackBar.open(this.papers[index].title + ' has been sent to editor.');
        this.papers[index].status = 'completed';
      }),
      (error => {
        const err = JSON.parse(error.error);
        this.snackBar.open(err.message);
      })
    );
  }

}
